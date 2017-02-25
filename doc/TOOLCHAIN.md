### TOOLCHAIN

Phases needed to produce and validate optimal matches

### Match finding

Performed by running the "find-matches" command. This creates a file with all
essential matches.
 
Number of essential matches can be much lower than the number
of all optimal matches and this makes it practical to store the essential
matches to disk after match finding.

Usage:

    find-matches <input> <finder> <min> <max> <essential>
      finds all optimal matches in input and stores the essential ones
      input: input file with original data
      finder: match finder, one of:
        bfmf: brute force match finder
        tmf: Tarsa match finder
      min: minimum match size, min >= 1, min <= max
      max: maximum match size, max >= min, max <= 120
      essential: file to store essential matches

### Match interpolation

Performed by running the "interpolate" command. This creates a file with all
optimal matches from a file containing only the essential ones.

Interpolation is a fast and sequential process and can be done using low
amount of memory if done in streaming way. This means that if you want to
integrate match finder into your program then you don't need to store
interpolated matches on disk.

Usage:

    interpolate <essential> <interpolated>
      reconstructs full set of optimal matches from essential ones
      essential: file with essential matches
      interpolated: file to store full set of optimal matches

### Match verifying

Performed by running the "verify" command. This verifies that a file contains
all optimal matches.

Verifying is used mainly for debugging purposes to test whether there is a bug
in the match producing steps.

Usage:

    verify <input> <interpolated>
      uses brute force search to verify presence of all optimal matches
      input: input file with original data
      interpolated: file with full set of optimal matches
