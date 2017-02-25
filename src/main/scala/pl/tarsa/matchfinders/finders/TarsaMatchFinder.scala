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

import java.util

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
    private val size =
      inputData.length

    private val suffixArray =
      Array.ofDim[Int](size)
    private val suffixArrayAuxiliary =
      Array.ofDim[Int](size)

    for (index <- 0 until size) {
      suffixArray(index) = index
    }

    private val backColumn =
      Array.ofDim[Byte](size)
    private val backColumnAuxiliary =
      Array.ofDim[Byte](size)

    for (index <- 1 until size) {
      backColumn(index) = inputData(index - 1)
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
        for (index <- startingIndex until startingIndex + elementsNumber) {
          activeColumn(index) = inputData(suffixArray(index) + lcpLength)
        }
        util.Arrays.fill(histogram, 0)
        for (index <- startingIndex until startingIndex + elementsNumber) {
          histogram(activeColumn(index) & 0xFF) += 1
        }
        if (lcpLength >= minMatch) {
          for (index <- startingIndex + 1 until
                 startingIndex + elementsNumber) {
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
          }
        }
        destinations(0) = startingIndex
        for (i <- destinations.indices.tail) {
          destinations(i) = destinations(i - 1) + histogram(i - 1)
        }
        for (index <- startingIndex until startingIndex + elementsNumber) {
          val value = activeColumn(index) & 0xFF
          suffixArrayAuxiliary(destinations(value)) = suffixArray(index)
          backColumnAuxiliary(destinations(value)) = backColumn(index)
          destinations(value) += 1
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
        for (i <- segments.indices.tail) {
          segments(i) = segments(i - 1) + histogram(i - 1)
        }
        for (i <- 0 until 256) {
          val segmentLength = segments(i + 1) - segments(i)
          if (segmentLength > 1) {
            radixSearchCached(lcpLength + 1, segments(i), segmentLength)
          }
        }
      } else {
        assert(lcpLength == maxMatch)
        for (index <- startingIndex + 1 until
               startingIndex + unsafeElementsNumber) {
          val optimalMatch =
            makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
          onAccepted(optimalMatch)
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
        util.Arrays.fill(histogram, 0)
        for (index <- startingIndex until startingIndex + elementsNumber) {
          histogram(getValue(index, lcpLength)) += 1
        }
        if (lcpLength >= minMatch) {
          for (index <- startingIndex + 1 until
                 startingIndex + elementsNumber) {
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
          }
        }
        destinations(0) = startingIndex
        for (i <- destinations.indices.tail) {
          destinations(i) = destinations(i - 1) + histogram(i - 1)
        }
        for (index <- startingIndex until startingIndex + elementsNumber) {
          val value = getValue(index, lcpLength)
          suffixArrayAuxiliary(destinations(value)) = suffixArray(index)
          destinations(value) += 1
        }
        Array.copy(suffixArrayAuxiliary,
                   startingIndex,
                   suffixArray,
                   startingIndex,
                   elementsNumber)
        val segments = segmentsStack(lcpLength)
        segments(0) = startingIndex
        for (i <- segments.indices.tail) {
          segments(i) = segments(i - 1) + histogram(i - 1)
        }
        for (i <- 0 until 256) {
          val segmentLength = segments(i + 1) - segments(i)
          if (segmentLength > 1) {
            radixSearch(lcpLength + 1, segments(i), segmentLength)
          }
        }
      } else {
        assert(lcpLength == maxMatch)
        for (index <- startingIndex + 1 until
               startingIndex + unsafeElementsNumber) {
          val optimalMatch =
            makePacked(suffixArray(index - 1), suffixArray(index), lcpLength)
          onAccepted(optimalMatch)
        }
      }
    }

    def result(): Unit =
      radixSearchCached(0, 0, size)
  }

  private def makePacked(source: Int, target: Int, length: Int): Match.Packed =
    Match.fromPositionLengthSource(target, length, source).packed
}
