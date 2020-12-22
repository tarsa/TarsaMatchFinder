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
from typing import BinaryIO

from tmf import number_codec


class Header:
    SIZE_ON_DISK: int = 8 + 4 + 2 + 2

    ESSENTIAL_MATCHES_MAGIC_NUMBER = 3463562352346342432
    INTERPOLATED_MATCHES_MAGIC_NUMBER = 3765472453426534653

    ALL_VALID_MAGIC_NUMBERS = {ESSENTIAL_MATCHES_MAGIC_NUMBER,
                               INTERPOLATED_MATCHES_MAGIC_NUMBER}

    def __init__(self, magic_number: int, input_size: int,
                 min_match: int, max_match: int):
        self.magic_number = magic_number
        self.input_size = input_size
        self.min_match = min_match
        self.max_match = max_match

    def validate(self) -> None:
        assert self.magic_number in Header.ALL_VALID_MAGIC_NUMBERS
        assert 0 <= self.input_size < (1 << 31)
        assert 1 <= self.min_match <= self.max_match <= 120

    def is_for_essential_matches(self) -> bool:
        return self.magic_number == Header.ESSENTIAL_MATCHES_MAGIC_NUMBER

    def is_for_interpolated_matches(self) -> bool:
        return self.magic_number == Header.INTERPOLATED_MATCHES_MAGIC_NUMBER

    @classmethod
    def for_essential_matches(cls, input_size: int,
                              min_match: int, max_match: int):
        return cls(cls.ESSENTIAL_MATCHES_MAGIC_NUMBER,
                   input_size, min_match, max_match)

    @classmethod
    def for_interpolated_matches(cls, input_size: int,
                                 min_match: int, max_match: int):
        return cls(cls.INTERPOLATED_MATCHES_MAGIC_NUMBER,
                   input_size, min_match, max_match)

    @staticmethod
    def from_file(input_file: BinaryIO):
        return Header(number_codec.read_big_endian_number(input_file, 8),
                      number_codec.read_big_endian_number(input_file, 4),
                      number_codec.read_big_endian_number(input_file, 2),
                      number_codec.read_big_endian_number(input_file, 2))

    def to_file(self, output_file: BinaryIO) -> None:
        number_codec.write_big_endian_number(self.magic_number, output_file, 8)
        number_codec.write_big_endian_number(self.input_size, output_file, 4)
        number_codec.write_big_endian_number(self.min_match, output_file, 2)
        number_codec.write_big_endian_number(self.max_match, output_file, 2)
