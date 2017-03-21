/*
 * Copyright (C) 2017 Piotr Tarsa ( http://github.com/tarsa )
 *
 *  This software is provided 'as-is', without any express or implied
 *  warranty.  In no event will the author be held liable for any damages
 *  arising from the use of this software.
 *
 *  Permission is granted to anyone to use this software for any purpose,
 *  including commercial applications, and to alter it and redistribute it
 *  freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software
 *     in a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *  3. This notice may not be removed or altered from any source distribution.
 *
 */
package pl.tarsa.matchfinders.finders

import pl.tarsa.matchfinders.collectors.MatchCollector
import pl.tarsa.matchfinders.model.Match

import scala.annotation.tailrec

object TarsaMatchFinder extends MatchFinder {
  var skippedStages = 0

  override def run(inputData: Array[Byte],
                   minMatch: Int,
                   maxMatch: Int,
                   collector: MatchCollector): Unit = {
    new Engine(inputData, minMatch, maxMatch, collector, skippedStages)
      .result()
  }

  private val RemappedAlphabetRadixSearchThreshold = 70

  private val LcpAwareInsertionSortThreshold = 10

  private class Engine(inputData: Array[Byte],
                       minMatch: Int,
                       maxMatch: Int,
                       collector: MatchCollector,
                       skippedStages: Int) {
    import collector.{onAccepted, onDiscarded}

    private val skipRadixSortCached = skippedStages > 3
    private val skipRadixSortRemapped = skippedStages > 2
    private val skipLcpAwareInsertionSort = skippedStages > 1
    private val skipLcpAwareInsertionSortMatchOutput = skippedStages > 0

    private var marker = 1L

    private val size =
      inputData.length

    private val suffixArray =
      Array.ofDim[Int](size)
    private val suffixArrayAuxiliary =
      Array.ofDim[Int](size)

    private val backColumn =
      Array.ofDim[Byte](size)
    private val backColumnAuxiliary =
      Array.ofDim[Byte](size)

    private val activeColumns =
      Array.ofDim[Int](size)
    private val activeColumnsAuxiliary =
      Array.ofDim[Int](size)

    private val histogram =
      Array.ofDim[Int](256)
    private val destinations =
      Array.ofDim[Int](256)

    private val markers =
      Array.ofDim[Long](256)
    private val alphabetMapping =
      Array.ofDim[Int](256)

    private val segmentsStack =
      Array.ofDim[Int](maxMatch, 256 + 1)

    private val lcpArray =
      Array.ofDim[Int](LcpAwareInsertionSortThreshold)

    private def initialize(): Unit = {
      var index = 0

      index = 0
      while (index < size) {
        suffixArray(index) = index
        index += 1
      }

      index = 1
      while (index < size) {
        backColumn(index) = inputData(index - 1)
        index += 1
      }
    }

    initialize()

    final def getValue(index: Int, depth: Int): Int = {
      inputData(suffixArray(index) + depth) & 0xFF
    }

    final def radixSearchCached(lcpLength: Int,
                                startingIndex: Int,
                                unsafeElementsNumber: Int): Unit = {
      var index = 0
      if (skipRadixSortCached) {
        ()
      } else if (unsafeElementsNumber < RemappedAlphabetRadixSearchThreshold) {
        radixSearchRemapped(lcpLength, startingIndex, unsafeElementsNumber)
      } else if (lcpLength < maxMatch) {
        val elementsNumber = {
          val lastIndex = startingIndex + unsafeElementsNumber - 1
          if (suffixArray(lastIndex) + lcpLength == size) {
            if (lcpLength >= minMatch && startingIndex != lastIndex) {
              val optimalMatch =
                makePacked(suffixArray(lastIndex - 1),
                           suffixArray(lastIndex),
                           lcpLength)
              if (suffixArray(lastIndex - 1) > 0 && suffixArray(lastIndex) > 0
                  && backColumn(lastIndex - 1) == backColumn(lastIndex)) {
                onDiscarded(optimalMatch)
              } else {
                onAccepted(optimalMatch)
              }
            }
            unsafeElementsNumber - 1
          } else {
            unsafeElementsNumber
          }
        }
        if ((lcpLength & 3) == 0) {
          index = startingIndex
          while (index < startingIndex + elementsNumber) {
            val chunkStart = suffixArray(index) + lcpLength
            if (chunkStart <= size - 4) {
              activeColumns(index) =
                ((inputData(chunkStart + 3) & 0xFF) << 24) +
                  ((inputData(chunkStart + 2) & 0xFF) << 16) +
                  ((inputData(chunkStart + 1) & 0xFF) << 8) +
                  (inputData(chunkStart) & 0xFF)
            } else {
              var symbolIndex = size - 1
              var chunk = 0
              while (symbolIndex >= chunkStart) {
                chunk <<= 8
                chunk |= inputData(symbolIndex) & 0xFF
                symbolIndex -= 1
              }
              activeColumns(index) = chunk
            }
            index += 1
          }
        } else {
          index = startingIndex
          while (index < startingIndex + elementsNumber) {
            activeColumns(index) >>>= 8
            index += 1
          }
        }
        if (lcpLength >= minMatch) {
          index = startingIndex + 1
          while (index < startingIndex + elementsNumber) {
            val optimalMatch =
              makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
            if (lcpLength < maxMatch && activeColumns(index - 1).toByte ==
                  activeColumns(index).toByte) {
              onDiscarded(optimalMatch)
            } else if (suffixArray(index - 1) > 0 && suffixArray(index) > 0 &&
                       backColumn(index - 1) == backColumn(index)) {
              onDiscarded(optimalMatch)
            } else {
              onAccepted(optimalMatch)
            }
            index += 1
          }
        }
        java.util.Arrays.fill(histogram, 0)
        index = startingIndex
        while (index < startingIndex + elementsNumber) {
          histogram(activeColumns(index) & 0xFF) += 1
          index += 1
        }
        destinations(0) = startingIndex
        index = 1
        while (index < destinations.length) {
          destinations(index) = destinations(index - 1) + histogram(index - 1)
          index += 1
        }
        index = startingIndex
        while (index < startingIndex + elementsNumber) {
          val value = activeColumns(index) & 0xFF
          val destination = destinations(value)
          suffixArrayAuxiliary(destination) = suffixArray(index)
          backColumnAuxiliary(destination) = backColumn(index)
          activeColumnsAuxiliary(destination) = activeColumns(index)
          destinations(value) += 1
          index += 1
        }
        Array.copy(suffixArrayAuxiliary,
                   startingIndex,
                   suffixArray,
                   startingIndex,
                   elementsNumber)
        Array.copy(backColumnAuxiliary,
                   startingIndex,
                   backColumn,
                   startingIndex,
                   elementsNumber)
        Array.copy(activeColumnsAuxiliary,
                   startingIndex,
                   activeColumns,
                   startingIndex,
                   elementsNumber)
        val segments = segmentsStack(lcpLength)
        segments(0) = startingIndex
        index = 1
        while (index < 256 + 1) {
          segments(index) = segments(index - 1) + histogram(index - 1)
          index += 1
        }
        var segmentIndex = 0
        while (segmentIndex < 256) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearchCached(lcpLength + 1,
                              segments(segmentIndex),
                              segmentLength)
          }
          segmentIndex += 1
        }
      } else {
        assert(lcpLength == maxMatch)
        index = startingIndex + 1
        while (index < startingIndex + unsafeElementsNumber) {
          val optimalMatch =
            makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
          onAccepted(optimalMatch)
          index += 1
        }
      }
    }

    final def radixSearchRemapped(lcpLength: Int,
                                  startingIndex: Int,
                                  unsafeElementsNumber: Int): Unit = {
      var index = 0
      if (skipRadixSortRemapped) {
        ()
      } else if (unsafeElementsNumber < LcpAwareInsertionSortThreshold) {
        lcpAwareInsertionSort(lcpLength, startingIndex, unsafeElementsNumber)
      } else if (lcpLength < maxMatch) {
        val elementsNumber = {
          val lastIndex = startingIndex + unsafeElementsNumber - 1
          if (suffixArray(lastIndex) + lcpLength == size) {
            if (lcpLength >= minMatch && startingIndex != lastIndex) {
              val optimalMatch =
                makePacked(suffixArray(lastIndex - 1),
                           suffixArray(lastIndex),
                           lcpLength)
              if (suffixArray(lastIndex - 1) > 0 && suffixArray(lastIndex) > 0
                  && backColumn(lastIndex - 1) == backColumn(lastIndex)) {
                onDiscarded(optimalMatch)
              } else {
                onAccepted(optimalMatch)
              }
            }
            unsafeElementsNumber - 1
          } else {
            unsafeElementsNumber
          }
        }
        if (lcpLength >= minMatch) {
          index = startingIndex + 1
          while (index < startingIndex + elementsNumber) {
            val optimalMatch =
              makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
            if (lcpLength < maxMatch &&
                getValue(index - 1, lcpLength) == getValue(index, lcpLength)) {
              onDiscarded(optimalMatch)
            } else if (suffixArray(index - 1) > 0 && suffixArray(index) > 0 &&
                       backColumn(index - 1) == backColumn(index)) {
              onDiscarded(optimalMatch)
            } else {
              onAccepted(optimalMatch)
            }
            index += 1
          }
        }
        var alphabetSize = 0
        index = startingIndex
        while (index < startingIndex + elementsNumber) {
          val value = getValue(index, lcpLength)
          if (markers(value) == marker) {
            val mappedValue = alphabetMapping(value)
            histogram(mappedValue) += 1
          } else {
            markers(value) = marker
            val mappedValue = alphabetSize
            alphabetSize += 1
            alphabetMapping(value) = mappedValue
            histogram(mappedValue) = 1
          }
          index += 1
        }
        marker += 1
        destinations(0) = startingIndex
        index = 1
        while (index < alphabetSize) {
          destinations(index) = destinations(index - 1) + histogram(index - 1)
          index += 1
        }
        index = startingIndex
        while (index < startingIndex + elementsNumber) {
          val mappedValue = alphabetMapping(getValue(index, lcpLength))
          assert(mappedValue < alphabetSize)
          val destination = destinations(mappedValue)
          suffixArrayAuxiliary(destination) = suffixArray(index)
          backColumnAuxiliary(destination) = backColumn(index)
          destinations(mappedValue) += 1
          index += 1
        }
        Array.copy(suffixArrayAuxiliary,
                   startingIndex,
                   suffixArray,
                   startingIndex,
                   elementsNumber)
        Array.copy(backColumnAuxiliary,
                   startingIndex,
                   backColumn,
                   startingIndex,
                   elementsNumber)
        val segments = segmentsStack(lcpLength)
        segments(0) = startingIndex
        index = 1
        while (index < alphabetSize + 1) {
          segments(index) = segments(index - 1) + histogram(index - 1)
          index += 1
        }
        var segmentIndex = 0
        while (segmentIndex < alphabetSize) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearchRemapped(lcpLength + 1,
                                segments(segmentIndex),
                                segmentLength)
          }
          segmentIndex += 1
        }
      } else {
        assert(lcpLength == maxMatch)
        index = startingIndex + 1
        while (index < startingIndex + unsafeElementsNumber) {
          val optimalMatch =
            makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
          onAccepted(optimalMatch)
          index += 1
        }
      }
    }

    final def lcpAwareInsertionSort(commonLcp: Int,
                                    suffixArrayStartingIndex: Int,
                                    elementsNumber: Int): Unit = {
      if (!skipLcpAwareInsertionSort) {
        lcpArray(0) = commonLcp
        var sortedElements = 1
        while (sortedElements < elementsNumber) {
          val suffixToInsert = suffixArray(
            suffixArrayStartingIndex + sortedElements)
          val backSymbolOfSuffixToInsert = backColumn(
            suffixArrayStartingIndex + sortedElements)
          val insertionPoint =
            insertAndReturnIndex(sortedElements - 1,
                                 suffixArrayStartingIndex,
                                 suffixToInsert,
                                 commonLcp,
                                 commonLcp,
                                 sortedElements)

          var index = suffixArrayStartingIndex + sortedElements
          while (index > suffixArrayStartingIndex + insertionPoint) {
            suffixArray(index) = suffixArray(index - 1)
            backColumn(index) = backColumn(index - 1)
            index -= 1
          }
          suffixArray(index) = suffixToInsert
          backColumn(index) = backSymbolOfSuffixToInsert
          sortedElements += 1
          if (!skipLcpAwareInsertionSortMatchOutput) {
            outputMatchesForInsertedSuffix(commonLcp,
                                           suffixArrayStartingIndex,
                                           insertionPoint,
                                           sortedElements)
          }
        }
      }
    }

    @tailrec
    final def insertAndReturnIndex(scannedPosition: Int,
                                   suffixArrayStartingIndex: Int,
                                   suffixToInsert: Int,
                                   previousLcp: Int,
                                   commonLcp: Int,
                                   sortedElements: Int): Int = {
      if (lcpArray(scannedPosition) < previousLcp) {
        lcpArray(scannedPosition + 1) = previousLcp
        scannedPosition + 1
      } else if (lcpArray(scannedPosition) > previousLcp) {
        if (scannedPosition > 0) {
          lcpArray(scannedPosition + 1) = lcpArray(scannedPosition)
          insertAndReturnIndex(scannedPosition - 1,
                               suffixArrayStartingIndex,
                               suffixToInsert,
                               previousLcp,
                               commonLcp,
                               sortedElements)
        } else {
          lcpArray(1) = lcpArray(0)
          lcpArray(0) = previousLcp
          0
        }
      } else {
        val lcp = computeLcp(
          suffixArray(suffixArrayStartingIndex + scannedPosition),
          suffixToInsert,
          previousLcp)
        val ordered = suffixesOrdered(
          suffixArray(suffixArrayStartingIndex + scannedPosition),
          suffixToInsert,
          lcp)
        if (ordered) {
          lcpArray(scannedPosition + 1) = previousLcp
          lcpArray(scannedPosition) = lcp
          scannedPosition + 1
        } else if (scannedPosition == 0) {
          lcpArray(1) = lcpArray(0)
          lcpArray(0) = lcp
          0
        } else {
          lcpArray(scannedPosition + 1) = lcpArray(scannedPosition)
          insertAndReturnIndex(scannedPosition - 1,
                               suffixArrayStartingIndex,
                               suffixToInsert,
                               lcp,
                               commonLcp,
                               sortedElements)
        }
      }
    }

    final def computeLcp(earlierSuffixStart: Int,
                         laterSuffixStart: Int,
                         knownLcp: Int): Int = {
      assert(earlierSuffixStart < laterSuffixStart)
      assert(laterSuffixStart + knownLcp <= size)
      val lcpLimit = math.min(maxMatch, size - laterSuffixStart)
      var currentLcp = knownLcp
      while (currentLcp < lcpLimit && {
               val earlierByte = inputData(earlierSuffixStart + currentLcp)
               val laterByte = inputData(laterSuffixStart + currentLcp)
               earlierByte == laterByte
             }) {
        currentLcp += 1
      }
      currentLcp
    }

    final def suffixesOrdered(firstSuffixStart: Int,
                              secondSuffixStart: Int,
                              lcp: Int): Boolean = {
      assert(firstSuffixStart != secondSuffixStart)
      assert(firstSuffixStart + lcp <= size)
      assert(secondSuffixStart + lcp <= size)
      if (firstSuffixStart + lcp == size) {
        false
      } else if (secondSuffixStart + lcp == size) {
        true
      } else if (lcp == maxMatch) {
        firstSuffixStart < secondSuffixStart
      } else {
        val firstSuffixByte = inputData(firstSuffixStart + lcp) & 0xFF
        val secondSuffixByte = inputData(secondSuffixStart + lcp) & 0xFF
        assert(firstSuffixByte != secondSuffixByte)
        firstSuffixByte < secondSuffixByte
      }
    }

    final def outputMatchesForInsertedSuffix(commonLcp: Int,
                                             suffixArrayStartingIndex: Int,
                                             insertionPoint: Int,
                                             sortedElements: Int): Unit = {
      val suffixToInsert = suffixArray(
        suffixArrayStartingIndex + insertionPoint)
      var currentMatchLength = -1
      if (insertionPoint > 0) {
        currentMatchLength = lcpArray(insertionPoint - 1)
      }
      if (lcpArray(insertionPoint) > currentMatchLength) {
        currentMatchLength = lcpArray(insertionPoint)
      }
      assert(currentMatchLength >= commonLcp)
      var prevBestMatchLength = -1
      var prevBestMatchSource = -1
      var lexSmallerScanIndex = insertionPoint - 1
      var lexSmallerScanLcp = -1
      if (lexSmallerScanIndex >= 0) {
        lexSmallerScanLcp = lcpArray(lexSmallerScanIndex)
      }
      var lexGreaterScanIndex = insertionPoint + 1
      var lexGreaterScanLcp = -1
      if (lexGreaterScanIndex < sortedElements) {
        lexGreaterScanLcp = lcpArray(lexGreaterScanIndex - 1)
      }
      val lcpLowerLimit = math.max(minMatch, commonLcp)
      while (currentMatchLength >= lcpLowerLimit) {
        var currentMatchSource = -1
        var currentMatchIndex = -1
        while (lexSmallerScanLcp == currentMatchLength) {
          val lexSmallerScanSource = suffixArray(
            suffixArrayStartingIndex + lexSmallerScanIndex)
          if (lexSmallerScanSource > currentMatchSource) {
            currentMatchSource = lexSmallerScanSource
            currentMatchIndex = lexSmallerScanIndex
          }
          lexSmallerScanIndex -= 1
          lexSmallerScanLcp = {
            if (lexSmallerScanIndex >= 0) {
              math.min(lexSmallerScanLcp, lcpArray(lexSmallerScanIndex))
            } else {
              -1
            }
          }
        }
        while (lexGreaterScanLcp == currentMatchLength) {
          val lexGreaterScanSource = suffixArray(
            suffixArrayStartingIndex + lexGreaterScanIndex)
          if (lexGreaterScanSource > currentMatchSource) {
            currentMatchSource = lexGreaterScanSource
            currentMatchIndex = lexGreaterScanIndex
          }
          lexGreaterScanIndex += 1
          lexGreaterScanLcp = {
            if (lexGreaterScanIndex < sortedElements) {
              math.min(lexGreaterScanLcp, lcpArray(lexGreaterScanIndex - 1))
            } else {
              -1
            }
          }
        }
        if (currentMatchSource > prevBestMatchSource) {
          val optimalMatch =
            makePacked(currentMatchSource, suffixToInsert, currentMatchLength)
          if (currentMatchLength == maxMatch || currentMatchSource == 0 ||
              backColumn(suffixArrayStartingIndex + currentMatchIndex) !=
                backColumn(suffixArrayStartingIndex + insertionPoint)) {
            onAccepted(optimalMatch)
          } else {
            onDiscarded(optimalMatch)
          }

          prevBestMatchLength = currentMatchLength
          prevBestMatchSource = currentMatchSource
        } else {
          val optimalMatch =
            makePacked(prevBestMatchSource, suffixToInsert, currentMatchLength)
          onDiscarded(optimalMatch)
        }
        currentMatchLength -= 1
      }
    }

    final def result(): Unit =
      radixSearchCached(0, 0, size)
  }

  private def makePacked(source: Int, target: Int, length: Int): Match.Packed =
    Match.fromPositionLengthSource(target, length, source).packed
}
