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
from array import array
from typing import BinaryIO

from tmf.header import Header
from tmf.match import Match


def brute_force_match_finder(input_file: BinaryIO,
                             min_match: int, max_match: int,
                             essential_matches_file: BinaryIO) -> None:
    # read input file
    input_file_size = os.path.getsize(input_file.name)
    input_data = array("B")
    input_data.fromfile(input_file, input_file_size)
    assert len(input_data) == input_file_size
    # start writing essential matches file
    essential_matches_file_header = \
        Header.for_essential_matches(input_file_size, min_match, max_match)
    essential_matches_file_header.validate()
    essential_matches_file_header.to_file(essential_matches_file)
    # variables
    inherited_offsets = [0 for _ in range(max_match + 1)]
    current_offsets = [0 for _ in range(max_match + 1)]
    inherited_max_match = 0
    # main loop
    for position in range(1, input_file_size):
        current_max_match = 0
        # collecting matches for current position
        offset = 1
        while offset <= position and current_max_match < max_match:
            match_length = Match.compute_match_length(
                input_data, position - offset, position, max_match)
            while current_max_match < match_length:
                current_max_match += 1
                current_offsets[current_max_match] = offset
            offset += 1
        # filtering and outputting matches
        for match_length in range(min_match, current_max_match + 1):
            current_is_inherited: bool = \
                match_length <= inherited_max_match and \
                inherited_offsets[match_length] == current_offsets[match_length]
            longer_has_same_offset: bool = \
                match_length < current_max_match and \
                current_offsets[match_length] == \
                current_offsets[match_length + 1]
            if (not current_is_inherited) and (not longer_has_same_offset):
                optimal_match = Match.from_position_length_offset(
                    position, match_length, current_offsets[match_length])
                optimal_match.validate(min_match, max_match)
                optimal_match.to_file(essential_matches_file)
        # inheriting matches
        for inherited_match_length in range(1, current_max_match):
            inherited_offsets[inherited_match_length] = \
                current_offsets[inherited_match_length + 1]
        inherited_max_match = current_max_match - 1
