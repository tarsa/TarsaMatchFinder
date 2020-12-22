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


def read_big_endian_number(input_file: BinaryIO, num_bytes: int) -> int:
    number = 0
    buffer = array("B")
    buffer.fromfile(input_file, num_bytes)
    for byte in buffer:
        number <<= 8
        number |= byte
    return number


def write_big_endian_number(number: int, output_file: BinaryIO,
                            num_bytes: int) -> None:
    buffer = array("B")
    for byte_index in range(num_bytes):
        buffer.append((number >> (8 * (num_bytes - 1 - byte_index))) & 255)
    buffer.tofile(output_file)
