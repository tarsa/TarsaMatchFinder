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
from typing import BinaryIO, Optional

from tmf.header import Header
from tmf.match import Match
from tmf.match_finder import BruteForceMatchFinder, FatHashMapMatchFinder


def verify(match_finder_name: str,
           input_file: BinaryIO, interpolated_matches_file: BinaryIO,
           progress_period: Optional[int]) -> None:
    # read input file
    input_file_size = os.path.getsize(input_file.name)
    input_data = array("B")
    input_data.fromfile(input_file, input_file_size)
    assert len(input_data) == input_file_size
    # read and validate interpolated matches file header
    header = Header.from_file(interpolated_matches_file)
    header.validate()
    assert header.is_for_interpolated_matches()
    assert len(input_data) == header.input_size
    # variables and match finder
    current_offsets = [0] * (header.max_match + 1)
    if match_finder_name == "bfmf":
        match_finder = BruteForceMatchFinder(input_data, header.min_match,
                                             header.max_match)
    elif match_finder_name == "hmmf":
        match_finder = FatHashMapMatchFinder(input_data, header.min_match,
                                             header.max_match)
    else:
        raise ValueError("Unknown match finder: " + match_finder_name)
    assert progress_period is None or progress_period >= 1
    next_progress_checkpoint = progress_period
    # match verification logic
    matches_read = 0
    try:
        for position in range(input_file_size):
            current_max_match = \
                match_finder.collect_matches_for_next_position(current_offsets)
            for match_length in range(header.min_match, current_max_match + 1):
                input_match = Match.from_file(interpolated_matches_file)
                input_match.validate(header.min_match, header.max_match)
                assert input_match.position == position
                assert input_match.length == match_length
                assert input_match.offset == current_offsets[match_length]
                matches_read += 1
            # display progress status
            if position + 1 == next_progress_checkpoint:
                print("Progress status: processed " +
                      f"{position + 1:,}".replace(",", " ") + " positions")
                next_progress_checkpoint += progress_period
    except Exception as e:
        raise ValueError("problem after reading " + str(matches_read) +
                         " matches") from e
    assert input_file.read(1) == b"", \
        "no further data expected in interpolated matches file"
    print("Verification OK")
