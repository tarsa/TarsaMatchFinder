# Copyright (C) 2020 Piotr Tarsa ( http://github.com/tarsa )
#
# This software is provided 'as-is', without any express or implied
# warranty.  In no event will the author be held liable for any damages
# arising from the use of this software.
#
# Permission is granted to anyone to use this software for any purpose,
# including commercial applications, and to alter it and redistribute it
# freely, subject to the following restrictions:
#
# 1. The origin of this software must not be misrepresented; you must not
#    claim that you wrote the original software. If you use this software
#    in a product, an acknowledgment in the product documentation would be
#    appreciated but is not required.
# 2. Altered source versions must be plainly marked as such, and must not be
#    misrepresented as being the original software.
# 3. This notice may not be removed or altered from any source distribution.
#
import os
from typing import List, Optional, BinaryIO

from tmf.header import Header
from tmf.match import Match


def interpolate(essential_matches_file: BinaryIO,
                interpolated_matches_file: BinaryIO,
                progress_period: Optional[int]) -> None:
    # start reading essential matches file
    essential_matches_header = Header.from_file(essential_matches_file)
    essential_matches_header.validate()
    assert essential_matches_header.is_for_essential_matches()
    input_size = essential_matches_header.input_size
    min_match = essential_matches_header.min_match
    max_match = essential_matches_header.max_match
    essential_matches_file_size = os.path.getsize(essential_matches_file.name)
    assert essential_matches_file_size >= Header.SIZE_ON_DISK and \
           (essential_matches_file_size - Header.SIZE_ON_DISK) \
           % Match.SIZE_ON_DISK == 0
    essential_matches_count = (essential_matches_file_size -
                               Header.SIZE_ON_DISK) // Match.SIZE_ON_DISK
    next_essential_match_index = 0
    next_essential_match: Optional[Match] = None

    def load_next_essential_match() -> None:
        nonlocal next_essential_match_index, next_essential_match
        if next_essential_match_index < essential_matches_count:
            next_essential_match_index += 1
            next_essential_match = Match.from_file(essential_matches_file)
            next_essential_match.validate(min_match, max_match)
        else:
            next_essential_match = None

    load_next_essential_match()
    # start writing interpolated matches file
    interpolated_matches_file_header = \
        Header.for_interpolated_matches(input_size, min_match, max_match)
    interpolated_matches_file_header.validate()
    interpolated_matches_file_header.to_file(interpolated_matches_file)
    # variables
    assert progress_period is None or progress_period >= 1
    next_progress_checkpoint = progress_period
    current_essential_matches: List[Match] = []
    inherited_offsets = [0] * (max_match + 1)
    current_offsets = [0] * (max_match + 1)
    inherited_max_match = 0
    # process matches
    for position in range(input_size):
        current_max_match = 0
        # reading and validating essential matches for current position
        current_essential_matches.clear()
        while next_essential_match and \
                next_essential_match.position == position:
            current_essential_matches.append(next_essential_match)
            load_next_essential_match()
        for index in range(1, len(current_essential_matches)):
            shorter = current_essential_matches[index - 1]
            longer = current_essential_matches[index]
            assert shorter.position == longer.position
            assert shorter.length < longer.length
            assert shorter.offset < longer.offset
        # unrolling essential matches
        next_match_length = min_match
        for current_essential_match in current_essential_matches:
            offset = position - current_essential_match.source
            assert \
                current_essential_match.length > inherited_max_match or \
                offset < inherited_offsets[current_essential_match.length], \
                "essential match must have smaller offset than inherited match"
            while next_match_length <= current_essential_match.length:
                current_offsets[next_match_length] = offset
                current_max_match = next_match_length
                next_match_length += 1
        # merge inherited matches with current matches
        for match_length in range(min_match, inherited_max_match + 1):
            if match_length <= current_max_match:
                current_offsets[match_length] = min(
                    current_offsets[match_length],
                    inherited_offsets[match_length])
            else:
                current_offsets[match_length] = inherited_offsets[match_length]
                assert match_length == max(current_max_match + 1, min_match)
                current_max_match = match_length
        # save current matches
        for match_length in range(min_match, current_max_match + 1):
            interpolated_match = Match.from_position_length_offset(
                position, match_length, current_offsets[match_length])
            interpolated_match.validate(min_match, max_match)
            interpolated_match.to_file(interpolated_matches_file)
        # inheriting matches
        for inherited_match_length in range(1, current_max_match):
            inherited_offsets[inherited_match_length] = \
                current_offsets[inherited_match_length + 1]
        inherited_max_match = current_max_match - 1
        # display progress status
        if position + 1 == next_progress_checkpoint:
            print("Progress status: processed " +
                  f"{position + 1:,}".replace(",", " ") + " positions")
            next_progress_checkpoint += progress_period
    print("Done")
