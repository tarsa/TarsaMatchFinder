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

object TarsaMatchFinder extends MatchFinder {
  override def run(inputData: Array[Byte],
                   minMatch: Int,
                   maxMatch: Int,
                   onAccepted: Match.Packed => Unit,
                   onDiscarded: Match.Packed => Unit): Unit = {
    new Engine(inputData, minMatch, maxMatch, onAccepted, onDiscarded).result()
  }

  private val CachedRadixSearchThreshold = 1234

  private class Engine(inputData: Array[Byte],
                       minMatch: Int,
                       maxMatch: Int,
                       onAccepted: Match.Packed => Unit,
                       onDiscarded: Match.Packed => Unit) {
    private var index = 0

    private val size =
      inputData.length

    private val suffixArray =
      Array.ofDim[Int](size)
    private val suffixArrayAuxiliary =
      Array.ofDim[Int](size)

    index = 0
    while (index < size) {
      suffixArray(index) = index
      index += 1
    }

    private val backColumn =
      Array.ofDim[Byte](size)
    private val backColumnAuxiliary =
      Array.ofDim[Byte](size)

    index = 1
    while (index < size) {
      backColumn(index) = inputData(index - 1)
      index += 1
    }

    private val activeColumn =
      Array.ofDim[Byte](size)

    private val histogram =
      Array.ofDim[Int](256)
    private val destinations =
      Array.ofDim[Int](256)

    private val segmentsStack =
      Array.ofDim[Int](maxMatch, 257)

    private def getValue(index: Int, depth: Int): Int = {
      inputData(suffixArray(index) + depth) & 0xFF
    }

    private def radixSearchCached(lcpLength: Int,
                                  startingIndex: Int,
                                  unsafeElementsNumber: Int): Unit = {
      if (unsafeElementsNumber < CachedRadixSearchThreshold) {
        radixSearch(lcpLength, startingIndex, unsafeElementsNumber)
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
        val segments = segmentsStack(lcpLength)
        segments(0) = startingIndex
        index = 1
        while (index < segments.length) {
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

    private def radixSearch(lcpLength: Int,
                            startingIndex: Int,
                            unsafeElementsNumber: Int): Unit = {
      if (lcpLength < maxMatch) {
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
        val segments = segmentsStack(lcpLength)
        segments(0) = startingIndex
        index = 1
        while (index < segments.length) {
          segments(index) = segments(index - 1) + histogram(index - 1)
          index += 1
        }
        var segmentIndex = 0
        while (segmentIndex < 256) {
          val segmentLength =
            segments(segmentIndex + 1) - segments(segmentIndex)
          if (segmentLength > 1) {
            radixSearch(lcpLength + 1, segments(segmentIndex), segmentLength)
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

    def result(): Unit =
      radixSearchCached(0, 0, size)
  }

  private def makePacked(source: Int, target: Int, length: Int): Match.Packed =
    Match.fromPositionLengthSource(target, length, source).packed
}
