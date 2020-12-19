### TarsaMatchFinder

Author: Piotr Tarsa

##### Complete match finder based on radix sort

TarsaMatchFinder outputs matches which can be used in LZ77-style data
compression algorithms. Unlike [other approaches][1] it doesn't only output
longest match per each position. Instead, for every position and match length in
specified interval (between min-match and max-match inclusive) it outputs a
match (if there's a match for that position and length) with smallest offset.

[1]: https://encode.su/threads/2710-LZ-M-T-amp-GPU-compression-decompression?p=51865&viewfull=1#post51865

##### Documentation

- [Setup + Usage](doc/SETUP.md) -
  How to set up the project and start using it
- [Toolchain](doc/TOOLCHAIN.md) -
  Phases needed to produce and validate optimal matches
- [Glossary](doc/GLOSSARY.md) -
  Terminology used in this project
- [Formats](doc/FORMATS.md) -
  Binary (on-disk) formats
- [Backlog](doc/BACKLOG.md) -
  List of to-do items
- [Trees](doc/TREES.md) -
  Sketching plans for using trees
