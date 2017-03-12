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

import pl.tarsa.matchfinders.model.Match

import scala.annotation.tailrec

object TarsaMatchFinder extends MatchFinder {
  var skippedStages = 0

  override def run(inputData: Array[Byte],
                   minMatch: Int,
                   maxMatch: Int,
                   onAccepted: Match.Packed => Unit,
                   onDiscarded: Match.Packed => Unit): Unit = {
    new Engine(inputData,
               minMatch,
               maxMatch,
               onAccepted,
               onDiscarded,
               skippedStages).result()
  }

  private val CachedColumnsRadixSearchThreshold = 1234

  private val RemappedAlphabetRadixSearchThreshold = 70

  private val LcpAwareInsertionSortThreshold = 10

  private class Engine(inputData: Array[Byte],
                       minMatch: Int,
                       maxMatch: Int,
                       onAccepted: Match.Packed => Unit,
                       onDiscarded: Match.Packed => Unit,
                       skippedStages: Int) {
    private val skipRadixSortCached = skippedStages > 4
    private val skipRadixSort = skippedStages > 3
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

    private val activeColumn =
      Array.ofDim[Byte](size)

    private val histogram =
      Array.ofDim[Int](256)
    private val destinations =
      Array.ofDim[Int](256)

    private val markers =
      Array.ofDim[Long](256)
    private val alphabetMapping =
      Array.ofDim[Int](256)

    private val segments =
      Array.ofDim[Int](maxMatch * (256 + 1))

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
                                unsafeElementsNumber: Int,
                                segmentsFrameStartingIndex: Int): Unit = {
      var index = 0
      if (skipRadixSortCached) {
        ()
      } else if (unsafeElementsNumber < CachedColumnsRadixSearchThreshold) {
        radixSearch(lcpLength,
                    startingIndex,
                    unsafeElementsNumber,
                    segmentsFrameStartingIndex)
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
        index = startingIndex
        while (index < startingIndex + elementsNumber) {
          activeColumn(index) = inputData(suffixArray(index) + lcpLength)
          index += 1
        }
        if (lcpLength >= minMatch) {
          index = startingIndex + 1
          while (index < startingIndex + elementsNumber) {
            val optimalMatch =
              makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
            if (lcpLength < maxMatch &&
                activeColumn(index - 1) == activeColumn(index)) {
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
          histogram(activeColumn(index) & 0xFF) += 1
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
          val value = activeColumn(index) & 0xFF
          suffixArrayAuxiliary(destinations(value)) = suffixArray(index)
          backColumnAuxiliary(destinations(value)) = backColumn(index)
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
        index = segmentsFrameStartingIndex
        segments(index) = startingIndex
        index += 1
        while (index < segmentsFrameStartingIndex + 256 + 1) {
          segments(index) = segments(index - 1) +
            histogram(index - 1 - segmentsFrameStartingIndex)
          index += 1
        }
        var segmentIndex = segmentsFrameStartingIndex
        while (segmentIndex < segmentsFrameStartingIndex + 256) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearchCached(lcpLength + 1,
                              segments(segmentIndex),
                              segmentLength,
                              segmentsFrameStartingIndex + 256 + 1)
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

    final def radixSearch(lcpLength: Int,
                          startingIndex: Int,
                          unsafeElementsNumber: Int,
                          segmentsFrameStartingIndex: Int): Unit = {
      var index = 0
      if (skipRadixSort) {
        ()
      } else if (unsafeElementsNumber < RemappedAlphabetRadixSearchThreshold) {
        radixSearchRemapped(lcpLength,
                            startingIndex,
                            unsafeElementsNumber,
                            segmentsFrameStartingIndex)
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
                  && getValue(lastIndex - 1, -1) == getValue(lastIndex, -1)) {
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
                       getValue(index - 1, -1) == getValue(index, -1)) {
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
          histogram(getValue(index, lcpLength)) += 1
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
          val value = getValue(index, lcpLength)
          suffixArrayAuxiliary(destinations(value)) = suffixArray(index)
          destinations(value) += 1
          index += 1
        }
        Array.copy(suffixArrayAuxiliary,
                   startingIndex,
                   suffixArray,
                   startingIndex,
                   elementsNumber)
        index = segmentsFrameStartingIndex
        segments(index) = startingIndex
        index += 1
        while (index < segmentsFrameStartingIndex + 256 + 1) {
          segments(index) = segments(index - 1) +
            histogram(index - 1 - segmentsFrameStartingIndex)
          index += 1
        }
        var segmentIndex = segmentsFrameStartingIndex
        while (segmentIndex < segmentsFrameStartingIndex + 256) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearch(lcpLength + 1,
                        segments(segmentIndex),
                        segmentLength,
                        segmentsFrameStartingIndex + 256 + 1)
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
                                  unsafeElementsNumber: Int,
                                  segmentsFrameStartingIndex: Int): Unit = {
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
                  && getValue(lastIndex - 1, -1) == getValue(lastIndex, -1)) {
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
                       getValue(index - 1, -1) == getValue(index, -1)) {
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
          suffixArrayAuxiliary(destinations(mappedValue)) = suffixArray(index)
          destinations(mappedValue) += 1
          index += 1
        }
        Array.copy(suffixArrayAuxiliary,
                   startingIndex,
                   suffixArray,
                   startingIndex,
                   elementsNumber)
        index = segmentsFrameStartingIndex
        segments(index) = startingIndex
        index += 1
        while (index < segmentsFrameStartingIndex + alphabetSize + 1) {
          segments(index) = segments(index - 1) +
            histogram(index - 1 - segmentsFrameStartingIndex)
          index += 1
        }
        var segmentIndex = segmentsFrameStartingIndex
        while (segmentIndex < segmentsFrameStartingIndex + alphabetSize) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearchRemapped(lcpLength + 1,
                                segments(segmentIndex),
                                segmentLength,
                                segmentsFrameStartingIndex + alphabetSize + 1)
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
          val insertionPoint =
            insertAndReturnIndex(sortedElements - 1,
                                 suffixArrayStartingIndex,
                                 suffixToInsert,
                                 commonLcp,
                                 commonLcp,
                                 sortedElements)
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
        suffixArray(suffixArrayStartingIndex + scannedPosition + 1) =
          suffixToInsert
        lcpArray(scannedPosition + 1) = previousLcp
        scannedPosition + 1
      } else if (lcpArray(scannedPosition) > previousLcp) {
        if (scannedPosition > 0) {
          suffixArray(suffixArrayStartingIndex + scannedPosition + 1) =
            suffixArray(suffixArrayStartingIndex + scannedPosition)
          lcpArray(scannedPosition + 1) = lcpArray(scannedPosition)
          insertAndReturnIndex(scannedPosition - 1,
                               suffixArrayStartingIndex,
                               suffixToInsert,
                               previousLcp,
                               commonLcp,
                               sortedElements)
        } else {
          suffixArray(suffixArrayStartingIndex + 1) = suffixArray(
            suffixArrayStartingIndex)
          lcpArray(1) = lcpArray(0)
          suffixArray(suffixArrayStartingIndex + 0) = suffixToInsert
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
          suffixArray(suffixArrayStartingIndex + scannedPosition + 1) =
            suffixToInsert
          lcpArray(scannedPosition + 1) = previousLcp
          lcpArray(scannedPosition) = lcp
          scannedPosition + 1
        } else if (scannedPosition == 0) {
          suffixArray(suffixArrayStartingIndex + 1) = suffixArray(
            suffixArrayStartingIndex)
          lcpArray(1) = lcpArray(0)
          suffixArray(suffixArrayStartingIndex + 0) = suffixToInsert
          lcpArray(0) = lcp
          0
        } else {
          suffixArray(suffixArrayStartingIndex + scannedPosition + 1) =
            suffixArray(suffixArrayStartingIndex + scannedPosition)
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
        while (lexSmallerScanLcp == currentMatchLength) {
          val lexSmallerScanSource = suffixArray(
            suffixArrayStartingIndex + lexSmallerScanIndex)
          if (lexSmallerScanSource > currentMatchSource) {
            currentMatchSource = lexSmallerScanSource
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
              inputData(currentMatchSource - 1) != inputData(
                suffixToInsert - 1)) {
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
      radixSearchCached(0, 0, size, 0)
  }

  private def makePacked(source: Int, target: Int, length: Int): Match.Packed =
    Match.fromPositionLengthSource(target, length, source).packed
}
