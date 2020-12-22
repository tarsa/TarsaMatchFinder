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


def verify(input_file: BinaryIO, interpolated_matches_file: BinaryIO) -> None:
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
    # match verification logic
    matches_read = 0
    for position in range(1, input_file_size):
        current_max_match = 0
        offset = 1
        while (offset <= position) and (current_max_match < header.max_match):
            try:
                match_length = Match.compute_match_length(
                    input_data, position - offset, position, header.max_match)
                while current_max_match < match_length:
                    current_max_match += 1
                    if current_max_match >= header.min_match:
                        input_match = Match.from_file(interpolated_matches_file)
                        input_match.validate(header.min_match, header.max_match)
                        assert input_match.position == position
                        assert input_match.length == current_max_match
                        assert input_match.offset == offset
                        matches_read += 1
            except Exception as e:
                raise ValueError("problem after reading " + str(matches_read) +
                                 " matches") from e
            offset += 1
    assert input_file.read(1) == b"", \
        "no further data expected in interpolated matches file"
    print("Verification OK")
