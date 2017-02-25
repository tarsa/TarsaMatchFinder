### SETUP

How to set up the project and start using it

### Requirements

- Java 8
- SBT (Scala Build Tool) or Lightbend Activator (which is a wrapper over SBT)

### Input data

- make directory `corpora` under the root directory of this project
- download Calgary Corpus and extract file `book1`
- move that file to previously created directory

### Sample usage

Recorded session from Linux shell:

    piotrek@piotr-desktop:~/projekty/TarsaMatchFinder$ java -version
    java version "1.8.0_121"
    Java(TM) SE Runtime Environment (build 1.8.0_121-b13)
    Java HotSpot(TM) 64-Bit Server VM (build 25.121-b13, mixed mode)
    piotrek@piotr-desktop:~/projekty/TarsaMatchFinder$ ll corpora/book1
    -rw-------  1 piotrek piotrek 768771 lut 25 21:59 corpora/book1
    piotrek@piotr-desktop:~/projekty/TarsaMatchFinder$ alias activ
    alias activ='~/devel/activator-1.3.12-minimal/bin/activator'
    piotrek@piotr-desktop:~/projekty/TarsaMatchFinder$ activ -mem 1000
    [info] Loading global plugins from /home/piotrek/.sbt/0.13/plugins
    [info] Loading project definition from /home/piotrek/projekty/TarsaMatchFinder/project
    [info] Set current project to TarsaMatchFinder (in build file:/home/piotrek/projekty/TarsaMatchFinder/)
    > run
    [info] Updating {file:/home/piotrek/projekty/TarsaMatchFinder/}tarsamatchfinder...
    [info] Resolving jline#jline;2.14.1 ...
    [info] Done updating.
    [info] Compiling 7 Scala sources to /home/piotrek/projekty/TarsaMatchFinder/target/scala-2.12/classes...
    [info] Running pl.tarsa.matchfinders.Main 
    Please specify a command
     Available commands (case sensitive):
      help
        displays this help
      find-matches <input> <finder> <min> <max> <essential>
        finds all optimal matches in input and stores the essential ones
        input: input file with original data
        finder: match finder, one of:
          bfmf: brute force match finder
          tmf: Tarsa match finder
        min: minimum match size, min >= 1, min <= max
        max: maximum match size, max >= min, max <= 120
        essential: file to store essential matches
      interpolate <essential> <interpolated>
        reconstructs full set of optimal matches from essential ones
        essential: file with essential matches
        interpolated: file to store full set of optimal matches
      verify <input> <interpolated>
        uses brute force search to verify presence of all optimal matches
        input: input file with original data
        interpolated: file with full set of optimal matches
          
    [success] Total time: 4 s, completed 2017-02-25 22:02:32
    > run find-matches corpora/book1 tmf 3 120 corpora/book1.flt
    [info] Running pl.tarsa.matchfinders.Main find-matches corpora/book1 tmf 3 120 corpora/book1.flt
    Essential matches written = 1138591
    [success] Total time: 1 s, completed 2017-02-25 22:03:11
    > run interpolate corpora/book1.flt corpora/book1.int
    [info] Running pl.tarsa.matchfinders.Main interpolate corpora/book1.flt corpora/book1.int
    Non-essential matches present = 0
    Interpolated matches written = 4090174
    [success] Total time: 0 s, completed 2017-02-25 22:03:36
    > run verify corpora/book1 corpora/book1.int
    [info] Running pl.tarsa.matchfinders.Main verify corpora/book1 corpora/book1.int
    Verification OK
    [success] Total time: 565 s, completed 2017-02-25 22:13:20
