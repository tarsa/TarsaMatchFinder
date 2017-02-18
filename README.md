### BACKLOG

### Overall items:
- better documentation
- change license?

### Algorithm quality and performance:
- better filtering of matches (important - do it first)
  - ideally the number of essential matches should be less than the number of 
    input bytes and then interpolator should be able to reconstruct the filtered
    ones quickly - I'm not sure if it's possible
- improve performance by high level optimizations like caching of data during
  sorting and special casing small segments in MSB radix sort
- rewrite in a lower level language (eg Rust) hoping for much better performance

### Code quality:
- refactor the total disgrace
- write automated tests
- implement better error checking and reporting
