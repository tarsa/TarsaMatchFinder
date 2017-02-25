### GLOSSARY

Terminology used in this project

### Match
A triple describing a repetition in input data. Representations used in this
project are:
- Match(position, length, offset) - where `offset` is greater than zero
- Match(position, length, source) - where `source` is less than `position`

`source` is equal to `position - offset` so those two representations carry
effectively the same information (and are actually implemented by single class).
Repetition means that following sub-sequences are identical:
- Input[source..source+length-1]
- Input[position..position+length-1]

### Optimal match
A match that has the smallest offset among a set of matches with given position
and length

### Essential match
An optimal match that cannot be interpolated from other essential matches 

### Match interpolation
Deriving of optimal matches from essential matches by means of match inheritance
and match shortening

### Match inheritance
Creating a new match from a match with lower position and higher length:
- existing match has a form of Match(position, length, offset) - where `length`
  is greater than one
- new match has a form of Match(position+1, length-1, offset)

### Match shortening
Creating a new match from a match with the same position and higher length:
- existing match has a form of Match(position, length, offset) - where `length`
  is greater than one
- new match has a form of Match(position, length-1, offset)
