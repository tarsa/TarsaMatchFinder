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
__author__ = 'Piotr Tarsa'

from typing import List


def main(args: List[str]) -> None:
    argc = len(args)
    if argc >= 2:
        command = args[1]
        params = args[2:]
        if command == "help":
            print_help()
        elif command == "find-matches":
            from tmf.match_finder import brute_force_match_finder
            check_command_parameters_count(command, argc, 5)
            assert params[1] == "bfmf", "only bfmf match finder is implemented"
            min_match = int(params[2])
            max_match = int(params[3])
            with open(params[0], "rb") as input_file, \
                    open(params[4], "w+b") as essential_matches_file:
                brute_force_match_finder(input_file, min_match, max_match,
                                         essential_matches_file)
        elif command == "interpolate":
            from tmf.interpolator import interpolate
            check_command_parameters_count(command, argc, 2)
            with open(params[0], "rb") as essential_matches_file, \
                    open(params[1], "w+b") as interpolated_matches_file:
                interpolate(essential_matches_file, interpolated_matches_file)
        elif command == "verify":
            from tmf.verifier import verify
            check_command_parameters_count(command, argc, 2)
            with open(params[0], "rb") as input_file, \
                    open(params[1], "rb") as interpolated_matches_file:
                verify(input_file, interpolated_matches_file)
        else:
            print_help()
            raise ValueError("Unknown command: " + command)
    else:
        print_help()
        raise ValueError("Please specify a command")


def check_command_parameters_count(command: str, argc: int,
                                   expected_count: int) -> None:
    params_count = argc - 2
    if params_count != expected_count:
        print_help()
        raise ValueError(
            "Error: wrong parameters count for command " + command +
            ". Expected " + str(expected_count) +
            ", got " + str(params_count) + ".")


def print_help() -> None:
    print("Available commands (case sensitive):",
          "  help",
          "    displays this help",
          "  find-matches <input> <finder> <min> <max> <essential>",
          "    finds all optimal matches in input and stores the essential ones",
          "    input: input file with original data",
          "    finder: match finder",
          "      must be bfmf (very slow brute force match finder)",
          "      as tmf (Tarsa match finder) is not present in lite version",
          "    min: minimum match size, min >= 1, min <= max",
          "    max: maximum match size, max >= min, max <= 120",
          "    essential: file to store essential matches",
          "  interpolate <essential> <interpolated>",
          "    reconstructs full set of optimal matches from essential ones",
          "    essential: file with essential matches",
          "    interpolated: file to store full set of optimal matches",
          "  verify <input> <interpolated>",
          "    uses brute force search to verify presence of all optimal matches",
          "    input: input file with original data",
          "    interpolated: file with full set of optimal matches",
          sep='\n', end='\n')
