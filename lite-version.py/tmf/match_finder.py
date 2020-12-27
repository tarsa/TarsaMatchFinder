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
from typing import BinaryIO, Optional, List, Union

from tmf.header import Header
from tmf.match import Match


class ExhaustiveMatchFinder:
    def __init__(self, input_data: array, min_match: int, max_match: int):
        self.input_data = input_data
        self.input_size = len(input_data)
        self.min_match = min_match
        self.max_match = max_match
        self.position = -1


class BruteForceMatchFinder(ExhaustiveMatchFinder):
    def collect_matches_for_next_position(
            self, offsets_buffer: List[int]) -> int:
        self.position += 1
        assert 0 <= self.position < self.input_size
        current_max_match = 0
        offsets_buffer[0] = 0
        offset = 1
        while offset <= self.position and current_max_match < self.max_match:
            match_length = Match.compute_match_length(
                self.input_data, self.position - offset,
                self.position, self.max_match)
            while current_max_match < match_length:
                current_max_match += 1
                offsets_buffer[current_max_match] = \
                    offset if current_max_match >= self.min_match else 0
            offset += 1
        for match_length in range(min(self.min_match, current_max_match + 1)):
            assert offsets_buffer[match_length] == 0
        for match_length in range(self.min_match, current_max_match + 1):
            assert offsets_buffer[match_length] > 0
        return current_max_match


class FatHashMapMatchFinder(ExhaustiveMatchFinder):
    def __init__(self, input_data: array, min_match: int, max_match: int):
        super().__init__(input_data, min_match, max_match)
        hash_lengths = []
        for match_length in range(max_match + 1):
            if match_length < min_match:
                hash_lengths.append(0)
            elif match_length < 20:
                hash_lengths.append(16)
            elif match_length < 50:
                hash_lengths.append(14)
            else:
                hash_lengths.append(12)
        self.hash_masks_by_match_length = \
            array("Q", [(1 << hash_length) - 1 for hash_length in hash_lengths])
        self.hash_maps_by_match_length: \
            List[Optional[List[Union[Optional[int], array]]]] = \
            [[None] * (1 << hash_lengths[match_length])
             if match_length >= min_match else None
             for match_length in range(max_match + 1)]

    def collect_matches_for_next_position(
            self, offsets_buffer: List[int]) -> int:
        def union_to_array(substrings_entry: Union[Optional[int],
                                                   array]) -> array:
            if substrings_entry is None:
                return array("Q", [])
            elif type(substrings_entry) is int:
                return array("Q", [substrings_entry])
            else:
                assert type(substrings_entry) is array and \
                       len(substrings_entry) > 1
                return substrings_entry

        def append_position(substrings_by_hash: List[Union[Optional[int],
                                                           array]],
                            substring_hash: int,
                            substrings_entry: Union[Optional[int], array],
                            new_substring_position: int) -> None:
            if substrings_entry is None:
                substrings_by_hash[substring_hash] = new_substring_position
            elif type(substrings_entry) is int:
                substrings_by_hash[substring_hash] = \
                    array("Q", [substrings_entry, new_substring_position])
            else:
                assert type(substrings_entry) is array
                substrings_entry.append(new_substring_position)

        self.position += 1
        assert 0 <= self.position < self.input_size
        current_max_match = 0
        offsets_buffer[0] = 0
        max_match = min(self.max_match, self.input_size - self.position)
        prefix_hash = hash(())
        last_matching_length = None
        last_matching_source = None
        last_matching_hash = None
        for match_length in range(1, max_match + 1):
            next_byte_index = self.position + match_length - 1
            assert 0 <= next_byte_index < self.input_size
            prefix_hash = hash(
                (prefix_hash, self.input_data[next_byte_index]))
            if match_length < self.min_match:
                continue
            substrings_by_hash = self.hash_maps_by_match_length[match_length]
            hash_mask = self.hash_masks_by_match_length[match_length]
            substrings_entry = substrings_by_hash[prefix_hash & hash_mask]
            substrings_for_hash = union_to_array(substrings_entry)
            if last_matching_source is not None and \
                    len(substrings_for_hash) > 1 and \
                    last_matching_source in substrings_for_hash:
                index = substrings_for_hash.index(last_matching_source)
                if index != 0:
                    substrings_for_hash[0], substrings_for_hash[index] = \
                        substrings_for_hash[index], substrings_for_hash[0]
            match_found = False
            for substring_index in range(len(substrings_for_hash)):
                source_pos = substrings_for_hash[substring_index]
                assert not match_found
                if last_matching_source == source_pos:
                    assert last_matching_length == match_length - 1
                    match_found = \
                        self.input_data[source_pos + match_length - 1] == \
                        self.input_data[self.position + match_length - 1]
                else:
                    match_found = Match.compute_match_length(
                        self.input_data, source_pos, self.position,
                        match_length) == match_length
                if match_found:
                    if current_max_match == 0:
                        current_max_match = match_length
                        assert current_max_match == self.min_match
                    else:
                        current_max_match += 1
                        assert current_max_match == match_length
                    offsets_buffer[current_max_match] = \
                        self.position - source_pos
                    assert substrings_entry is not None
                    if type(substrings_entry) is int:
                        substrings_by_hash[prefix_hash & hash_mask] = \
                            self.position
                    else:
                        assert type(substrings_entry) is array and \
                               len(substrings_entry) > 1
                        substrings_entry[substring_index] = self.position
                        # bring found item closer to the front
                        if substring_index >= 2:
                            middle = substring_index // 2
                            a = substrings_entry
                            a[middle], a[substring_index] = \
                                a[substring_index], a[middle]
                        elif substring_index == 1:
                            a = substrings_entry
                            a[0], a[1] = a[1], a[0]
                    last_matching_length = match_length
                    last_matching_source = source_pos
                    last_matching_hash = prefix_hash
                    break
            if not match_found:
                break
        if last_matching_length is None:
            assert current_max_match == 0
            if self.input_size - self.position >= self.min_match:
                recreated_hash = hash(())
                for byte_index in range(self.min_match):
                    next_byte = self.input_data[self.position + byte_index]
                    recreated_hash = hash((recreated_hash, next_byte))
                assert recreated_hash == prefix_hash
                substrings_by_hash = \
                    self.hash_maps_by_match_length[self.min_match]
                hash_mask = self.hash_masks_by_match_length[self.min_match]
                substrings_entry = substrings_by_hash[prefix_hash & hash_mask]
                substrings_for_hash = union_to_array(substrings_entry)
                assert self.position not in substrings_for_hash
                append_position(substrings_by_hash, prefix_hash & hash_mask,
                                substrings_entry, self.position)
        elif current_max_match < max_match:
            assert self.min_match <= last_matching_length == current_max_match
            assert last_matching_source is not None
            assert last_matching_hash is not None
            full_match_length = Match.compute_match_length(
                self.input_data,
                last_matching_source + current_max_match,
                self.position + current_max_match,
                max_match - current_max_match) + current_max_match
            assert full_match_length >= current_max_match
            assert full_match_length <= max_match
            # no need to create missing entries for previous suffix if it
            # shares at least max_match bytes with current one
            # there would be no branching anyway
            if full_match_length < max_match:
                prefix_hash = last_matching_hash
                # add intermediate levels on shared path
                for match_length in range(current_max_match + 1,
                                          full_match_length + 1):
                    next_byte_index = self.position + match_length - 1
                    assert 0 <= next_byte_index < self.input_size
                    prefix_hash = hash(
                        (prefix_hash, self.input_data[next_byte_index]))
                    substrings_by_hash = \
                        self.hash_maps_by_match_length[match_length]
                    hash_mask = self.hash_masks_by_match_length[match_length]
                    substrings_entry = \
                        substrings_by_hash[prefix_hash & hash_mask]
                    substrings_for_hash = union_to_array(substrings_entry)
                    assert last_matching_source not in substrings_for_hash and \
                           self.position not in substrings_for_hash
                    append_position(substrings_by_hash, prefix_hash & hash_mask,
                                    substrings_entry, self.position)
                # add missing branches at level full_match_length + 1
                for source_pos in [last_matching_source, self.position]:
                    branch_byte_index = source_pos + full_match_length
                    top_prefix_hash = hash((prefix_hash,
                                            self.input_data[branch_byte_index]))
                    substrings_by_hash = \
                        self.hash_maps_by_match_length[full_match_length + 1]
                    hash_mask = \
                        self.hash_masks_by_match_length[full_match_length + 1]
                    substrings_entry = \
                        substrings_by_hash[top_prefix_hash & hash_mask]
                    substrings_for_hash = union_to_array(substrings_entry)
                    if full_match_length == current_max_match and \
                            source_pos == last_matching_source:
                        if source_pos not in substrings_for_hash:
                            append_position(substrings_by_hash,
                                            top_prefix_hash & hash_mask,
                                            substrings_entry, source_pos)
                    else:
                        assert source_pos not in substrings_for_hash
                        append_position(substrings_by_hash,
                                        top_prefix_hash & hash_mask,
                                        substrings_entry, source_pos)
            for match_length in range(current_max_match + 1,
                                      full_match_length + 1):
                current_max_match += 1
                offsets_buffer[current_max_match] = \
                    self.position - last_matching_source
                assert current_max_match == match_length
        for match_length in range(min(self.min_match, current_max_match + 1)):
            assert offsets_buffer[match_length] == 0
        for match_length in range(self.min_match, current_max_match + 1):
            assert offsets_buffer[match_length] > 0
        return current_max_match


def find_all_essential_matches(
        match_finder_name: str, min_match: int, max_match: int,
        input_file: BinaryIO, essential_matches_file: BinaryIO,
        progress_period: Optional[int]) -> None:
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
    # variables and match finder
    inherited_offsets = [0] * (max_match + 1)
    current_offsets = [0] * (max_match + 1)
    inherited_max_match = 0
    if match_finder_name == "bfmf":
        match_finder = BruteForceMatchFinder(input_data, min_match, max_match)
    elif match_finder_name == "hmmf":
        match_finder = FatHashMapMatchFinder(input_data, min_match, max_match)
    else:
        raise ValueError("Unknown match finder: " + match_finder_name)
    assert progress_period is None or progress_period >= 1
    next_progress_checkpoint = progress_period
    # main loop
    for position in range(input_file_size):
        # collecting matches for current position
        current_max_match = \
            match_finder.collect_matches_for_next_position(current_offsets)
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
        # display progress status
        if position + 1 == next_progress_checkpoint:
            print("Progress status: processed " +
                  f"{position + 1:,}".replace(",", " ") + " positions")
            next_progress_checkpoint += progress_period
    print("Done")
