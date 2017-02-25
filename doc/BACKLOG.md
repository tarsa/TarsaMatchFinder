### BACKLOG

List of to-do items

### Overall
- better documentation
- change license?

### Algorithm quality and performance
- process files in streaming way where feasible
- better filtering of matches if it's feasible for both match finders
  - ideally the number of essential matches should be less than the number of 
    input bytes and then interpolator should be able to reconstruct the
    discarded ones quickly - I'm not sure if it's possible
- improve performance by high level optimizations like caching of data during
  sorting for big segments and alphabet remapping for small segments in MSB
  radix sort
- rewrite in a lower level language (eg Rust) hoping for much better performance

### Code quality
- refactor the total disgrace
- write automated tests
- implement better error checking and reporting

### Other
- rename TarsaMatchFinder to TarsaRadixMatchFinder as a suffix tree match finder
  is possible (but very hard to get right, I think)
