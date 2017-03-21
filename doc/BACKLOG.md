### BACKLOG

List of to-do items

### Overall
- better documentation
- change license?

### Algorithm quality and resource usage
- cache more columns (4 or 8 instead of 1) at once during cached radix sort
  passes
- develop better heuristics for switching to LCP-aware insertion sort
- make genetic algorithm to find best parameters (thresholds) for
  TarsaMatchFinder
- reduce memory requirements for TarsaMatchFinder
  - that would require to output matches in more ordered way
  - to achieve that we can divide input to segments and output matches for
    subsequent segments
  - therefore we need to somehow merge match data structures from current
    segment and previous segments
  - for previous segments we can use merged compact trie
  - after finishing processing a segment in isolation we can turn it into
    unmerged compacted trie
  - during merging compacted tries for current segment and previous segments we
    can output matches for current segment that originate in previous segments
  - with small enough single segment size above scheme would make total memory
    requirements proportional to the size of merged compacted trie
  - the merged compacted trie can be adapted to work on a sliding window by 
    pruning suffixes during merge with current segment's compacted trie
- make multi-threaded TarsaMatchFinder
- better filtering of matches if it's feasible for both match finders
  - ideally the number of essential matches should be less than the number of 
    input bytes and then interpolator should be able to reconstruct the
    discarded ones quickly - I'm not sure if it's possible
- rewrite in a lower level language (eg Rust and/ or C) hoping for much better
  performance

### Code quality
- refactor the total disgrace
- write automated tests
- implement better error checking and reporting

### Other
- show info from file headers (on error and on demand)
