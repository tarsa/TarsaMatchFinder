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
from array import array
from typing import BinaryIO

from tmf import number_codec


class Match:
    SIZE_ON_DISK: int = 4 * 4

    def __init__(self, position: int, length: int, offset: int):
        self.position = position
        self.length = length
        self.offset = offset
        self.source = position - offset

    def validate(self, min_match: int, max_match: int) -> None:
        assert 1 <= self.offset <= self.position < (1 << 31)
        assert 1 <= min_match <= self.length <= max_match <= 120

    def __lt__(self, other) -> bool:
        return (self.position, self.length, self.offset) < \
               (other.position, other.length, other.offset)

    @staticmethod
    def from_position_length_offset(position: int, length: int, offset: int):
        return Match(position, length, offset)

    @staticmethod
    def from_position_length_source(position: int, length: int, source: int):
        return Match(position, length, position - source)

    @staticmethod
    def from_file(input_file: BinaryIO):
        result = Match(number_codec.read_big_endian_number(input_file, 4),
                       number_codec.read_big_endian_number(input_file, 4),
                       number_codec.read_big_endian_number(input_file, 4))
        assert number_codec.read_big_endian_number(input_file, 4) == 0
        return result

    def to_file(self, output_file: BinaryIO) -> None:
        number_codec.write_big_endian_number(self.position, output_file, 4)
        number_codec.write_big_endian_number(self.length, output_file, 4)
        number_codec.write_big_endian_number(self.offset, output_file, 4)
        number_codec.write_big_endian_number(0, output_file, 4)

    @staticmethod
    def compute_match_length(input_data: array, source_pos: int,
                             target_pos: int, max_match: int) -> int:
        input_length = len(input_data)
        match_length = 0
        while (source_pos + match_length < input_length) and \
                (target_pos + match_length < input_length) and \
                (input_data[source_pos + match_length] ==
                 input_data[target_pos + match_length]) and \
                (match_length < max_match):
            match_length += 1
        return match_length
