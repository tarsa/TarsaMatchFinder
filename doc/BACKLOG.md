### BACKLOG

List of to-do items

### Overall
- better documentation
- change license?

### Algorithm quality and resource usage
- make segments stack in TarsaMatchFinder contiguous to improve CPU caching
- use LCP-aware insertion sort for smallest segments in TarsaMatchFinder
- cache more columns (4 instead of 1) at once during cached radix sort passes
- maybe use cached back column in more radix sort variants
- do essential matches sorting as a separate phase
- process files in streaming way where feasible
- make multi-threaded TarsaMatchFinder
- make genetic algorithm to find best parameters (thresholds) for
  TarsaMatchFinder
- better filtering of matches if it's feasible for both match finders
  - ideally the number of essential matches should be less than the number of 
    input bytes and then interpolator should be able to reconstruct the
    discarded ones quickly - I'm not sure if it's possible
- rewrite in a lower level language (eg Rust) hoping for much better performance

### Code quality
- refactor the total disgrace
- write automated tests
- implement better error checking and reporting

### Other
- show info from file headers (on error and on demand)
- rename TarsaMatchFinder to TarsaRadixSortMatchFinder as a suffix tree match
  finder is possible (but very hard to get right, I think)
