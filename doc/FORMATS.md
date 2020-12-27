### FORMATS

Binary (on-disk) formats

### Match

- big endian encoding
- four double-words per match
  - position
  - length
  - offset
  - 0 (padding to get to 16-bytes per match)

### Essential matches file

- big endian encoding
- header containing four items
  - magic number (long) = 3463562352346342432l
  - size of original input file (int)
  - minimum match length (short)
  - maximum match length (short)
- sequence of matches described above
  - sorted by position then offset or length
    (both ways yield same order for valid data)

### Interpolated matches file

- big endian encoding
- header containing four items
  - magic number (long) = 3765472453426534653l
  - size of original input file (int)
  - minimum match length (short)
  - maximum match length (short)
- sequence of matches described above
  - sorted by position then offset or length
    (both ways yield same order for valid data)
