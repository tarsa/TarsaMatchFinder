### TREES

Sketching plans for using trees

### The problem

Current approach which is to output matches only during radix sorting has two
big problems:
- high memory requirements
  - the modified radix sort uses a lot of space
  - outputted matches are large in number and they need to be buffered and
    sorted before further usage
- lack of streaming mode
  - streaming mode is useful to produce matches with limited offsets - to the
    size of a sliding window 
  - it is often used to process files which are too big to process at once
  - easy solution would be to do radix sorting on overlapping segments, but that
    adds problems
    - even higher memory requirements
    - sorting and analyzing suffixes more than once

### The solution

A possible solution is to employ enriched tries specially crafted for finding
matches during a merge of two such tries. Steps to get to that point are as
follows:
- input is divided to independent consecutive segments
- each segment is processed individually by radix sort match finder
- individual handling of segments mean that matches that are found do not span
  segment boundaries
- to find matches that span segment boundaries tries are used
- after processing a segment by radix sort match finder the created suffix array
  is converted to a trie, here called segment trie
- during processing the consecutive segments a main trie is kept in memory
- main trie is empty at the beginning of the whole process
- after processing the first segment the segment trie becomes the main trie
- after processing some later segment the resulting segment trie is merged with
  a then non-empty main trie
- during that merge matches that originate in main trie and end in segment trie
  are outputted
- matches outputted during radix sorting a segment together with matches
  outputted when merging segment trie with main trie form a whole set of matches
  for that segment
- when the main trie grows too big too old suffixes have to be pruned
- pruning old suffixes can be done during the merge of main trie and segment
  trie

From the above process it is immediately visible that outputting matches ending
in a particular segment happens only during processing of that segment. That
reduces memory requirements considerably as there is no need to buffer all
matches (from the whole input) to sort them by target position before outputting
and consuming (assuming the consumption is done in a streaming fashion).

### Trie structure

The only trie purpose is to facilitate finding matches when merging two tries,
therefore fast search is not needed. Two tries must be merged using a linear
scan of both tries and linear output of a new trie. During a merge random access
to the sliding window of input data is needed.

Leaf fields:
- 1 byte - depth with a special marker value (zero) that tells this is a leaf
- 1-2 bytes - cached first bytes on incoming edge to reduce the number of random
  memory accesses
- 1 byte (only segment trie) - length of longest match outputted during radix
  sort match finding
- 4 bytes - suffix index

Node fields:
- 1 byte - depth (above zero) which is the length of longest common prefix of
  all children
- 1-2 bytes - cached first bytes on incoming edge to reduce the number of random
  memory accesses
- 1 byte - (children number - 1)
- 4 bytes (only main trie) - highest suffix index of all descendants

### Bonus: low RAM usage mode

Above scheme has a big potential strong point - ability to reduce memory
overhead significantly without making the whole process impractically slow. The
key observations needed for making that happen are:
- segment size can be tuned freely - smaller segment size reduces memory
  overhead, but increases the number of merges of main trie and segment trie
- sliding window must be present in memory during the merge of tries because of
  potentially many random memory accesses to that sliding window
- tries can be both read and written almost fully sequentially and that makes
  them perfect candidates to store on hard disk (through the means of memory
  mapped files)
- matches before sorting can be buffered on disk
- other parts of the algorithm can also store data to disk and/ or reload data
  from disk when needed
  
In an extreme case the whole match-finding process can be done with little
memory overhead - i.e. having memory requirements not much higher than the
sliding window size (eg 20%). Here is how to achieve that:
- assume N segments are already processed and no memory is allocated
- on disk there are
  - main trie
  - input data
- a single segment need to be individually processed
  - input data for segment is loaded from disk
  - segment suffixes are sorted
  - matches are outputted to disk
  - suffix array is converted to segment trie and outputted to disk in streaming
    way
  - now only memory with input data for segment is allocated
- segment trie and main trie need to be merged
  - sliding window for main trie is loaded to memory
  - main trie and segment tries are linearly scanned and a resulting trie is
    written to disk in streaming way
  - during above merge matches are outputted to disk
  - memory for input data (both main and segment) is unallocated
  - now no memory is allocated
- matches need to be sorted before consumption
  - load all matches ending in currently processed segment to memory
  - sort them
  - feed the consumer of matches with them
  
If processing a single segment has an overhead of N times then the segment 
size needs to be 1/ N of the input size to make the whole process having
overhead of 1/ N of input size (that extra space is needed for input data for
segment during merge with main trie). But is also means that there will be N
times more merges than in situation where segment size is equal to sliding
window size. Therefore, it is needed to reduce the overhead of processing a
single segment to a minimum.

Reducing memory overhead of processing a single segment is mainly achieved by
reducing the memory used by radix sort. This can be done by:
- not caching any of the columns
- achieving stable sorting by doing unstable sorting followed by sorting of
  equal substrings by their index
- not allocating the LCP array
- outputting the matches straight into a disk
- in fact the only essential data structures needed for efficient radix search
  based match finding are input data for segment and suffix array
- total memory consumption for processing a segment would then be 5x the segment
  size

Size of matches for a single segment can easily exceed the 5x bound computed
above. To fit in the limit the matches have to be sorted in phases:
- first they need to be split to buckets
- each bucket need to be sorted individually
- then the buckets with matches have to be consumed sequentially
