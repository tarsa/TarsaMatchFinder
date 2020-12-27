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

from typing import List, Optional


def main(args: List[str]) -> None:
    argc = len(args)
    if argc >= 2:
        command = args[1]
        params = args[2:]
        params_count = len(params)
        if command == "help":
            print_help()
        elif command == "find-matches":
            from tmf.match_finder import find_all_essential_matches
            check_command_parameters_count(command, params_count, 5, 6)
            match_finder_name = params[0]
            min_match = int(params[1])
            max_match = int(params[2])
            progress_period = parse_progress_period(params, 5)
            with open(params[3], "rb") as input_file, \
                    open(params[4], "w+b") as essential_matches_file:
                find_all_essential_matches(
                    match_finder_name, min_match, max_match,
                    input_file, essential_matches_file, progress_period)
        elif command == "interpolate":
            from tmf.interpolator import interpolate
            check_command_parameters_count(command, params_count, 2, 3)
            progress_period = parse_progress_period(params, 2)
            with open(params[0], "rb") as essential_matches_file, \
                    open(params[1], "w+b") as interpolated_matches_file:
                interpolate(essential_matches_file, interpolated_matches_file,
                            progress_period)
        elif command == "verify":
            from tmf.verifier import verify
            check_command_parameters_count(command, params_count, 3, 4)
            match_finder_name = params[0]
            progress_period = parse_progress_period(params, 3)
            with open(params[1], "rb") as input_file, \
                    open(params[2], "rb") as interpolated_matches_file:
                verify(match_finder_name, input_file, interpolated_matches_file,
                       progress_period)
        else:
            print_help()
            raise ValueError("Unknown command: " + command)
    else:
        print_help()
        raise ValueError("Please specify a command")


def check_command_parameters_count(command: str, params_count: int,
                                   min_count: int, max_count: int) -> None:
    if params_count < min_count or params_count > max_count:
        print_help()
        raise ValueError(
            "Error: wrong parameters count for command " + command +
            ". Expected between " + str(min_count) + " and " + str(max_count) +
            ", got " + str(params_count) + ".")


def parse_progress_period(params: List[str],
                          param_index: int) -> Optional[int]:
    if param_index < len(params):
        param = params[param_index]
        period = int(float(param))
        assert period == float(param), "progress period must be integral"
        assert period >= 1, "progress period must be positive"
        return period
    else:
        return None


def print_help() -> None:
    print("Available commands (case sensitive):",
          "  help",
          "    displays this help",
          "  find-matches <finder> <min> <max> <input> <essential> <progress>",
          "    finds all optimal matches in input and stores the essential ones",
          "    finder: match finder, one of:",
          "      bfmf: very slow brute force match finder",
          "      hmmf: very memory hungry fat hash map match finder",
          "      NOTE: tmf (Tarsa match finder) is not present in lite version",
          "    min: minimum match size, min >= 1, min <= max",
          "    max: maximum match size, max >= min, max <= 120",
          "    input: input file with original data",
          "    essential: file to store essential matches",
          "    progress: optional period in bytes",
          "      if present then show progress status periodically",
          "  interpolate <essential> <interpolated> <progress>",
          "    reconstructs full set of optimal matches from essential ones",
          "    essential: file with essential matches",
          "    interpolated: file to store full set of optimal matches",
          "    progress: optional period in bytes",
          "      if present then show progress status periodically",
          "  verify <finder> <input> <interpolated> <progress>",
          "    verifies presence of all optimal matches after interpolation",
          "    finder: match finder, one of:",
          "      bfmf: very slow brute force match finder",
          "      hmmf: very memory hungry fat hash map match finder",
          "    input: input file with original data",
          "    interpolated: file with full set of optimal matches",
          "    progress: optional period in bytes",
          "      if present then show progress status periodically",
          sep='\n', end='\n')
