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
package pl.tarsa.matchfinders.interpolation

import pl.tarsa.matchfinders.model.Match

import scala.collection.mutable

class Interpolator {
  def run(inputSize: Int,
          minMatch: Int,
          maxMatch: Int,
          essentialMatches: Array[Match.Packed]): Array[Match.Packed] = {
    // variables
    val interpolatedMatchesArrayBuilder =
      mutable.ArrayBuilder.make[Match.Packed]()
    val currentEssentialMatches =
      Array.ofDim[Match.Packed](maxMatch - minMatch + 1)
    val inheritedOffsets = Array.ofDim[Int](maxMatch + 1)
    val currentOffsets = Array.ofDim[Int](maxMatch + 1)
    var currentEssentialMatchesNumber = 0
    var currentEssentialMatchIndex = 0
    var inheritedMaxMatch = 0
    var currentMaxMatch = 0
    var position = 0
    var matchLength = 0
    var nextMatchIndex = 0
    var offset = 0
    var nonEssentialOnesCounter = 0
    // main loop
    position = 0
    while (position < inputSize) {
      // inheriting matches
      matchLength = 2
      while (matchLength <= currentMaxMatch) {
        inheritedOffsets(matchLength - 1) = currentOffsets(matchLength)
        matchLength += 1
      }
      inheritedMaxMatch = currentMaxMatch - 1
      // clearing current matches
      currentMaxMatch = 0
      // reading essential matches
      currentEssentialMatchesNumber = 0
      while (nextMatchIndex < essentialMatches.length && Match(
               essentialMatches(nextMatchIndex)).position == position) {
        currentEssentialMatches(currentEssentialMatchesNumber) =
          essentialMatches(nextMatchIndex)
        currentEssentialMatchesNumber += 1
        nextMatchIndex += 1
      }
      // sorting essential matches
      java.util.Arrays
        .sort(currentEssentialMatches, 0, currentEssentialMatchesNumber)
      // unrolling essential matches
      currentEssentialMatchIndex = 0
      matchLength = minMatch
      while (currentEssentialMatchIndex < currentEssentialMatchesNumber) {
        val currentEssentialMatch = Match(
          currentEssentialMatches(currentEssentialMatchIndex))
        val samePositionAndLength = currentEssentialMatchIndex > 0 &&
            currentEssentialMatch.length == Match(
              currentEssentialMatches(currentEssentialMatchIndex - 1)).length
        offset = position - currentEssentialMatch.source
        val matchWasInherited =
          currentEssentialMatch.length <= inheritedMaxMatch &&
            offset == inheritedOffsets(currentEssentialMatch.length)
        if (samePositionAndLength || matchWasInherited) {
          nonEssentialOnesCounter += 1
        }
        while (matchLength <= currentEssentialMatch.length) {
          currentOffsets(matchLength) = offset
          currentMaxMatch = matchLength
          matchLength += 1
        }
        currentEssentialMatchIndex += 1
      }
      // merge inherited matches with current matches
      matchLength = minMatch
      while (matchLength <= inheritedMaxMatch) {
        if (matchLength <= currentMaxMatch) {
          currentOffsets(matchLength) = Math.min(inheritedOffsets(matchLength),
                                                 currentOffsets(matchLength))
        } else {
          currentMaxMatch = matchLength
          currentOffsets(matchLength) = inheritedOffsets(matchLength)
        }
        matchLength += 1
      }
      // save current matches
      matchLength = minMatch
      while (matchLength <= currentMaxMatch) {
        interpolatedMatchesArrayBuilder +=
          Match
            .fromPositionLengthOffset(
              position,
              matchLength,
              currentOffsets(matchLength)
            )
            .packed
        matchLength += 1
      }
      // advance to next iteration
      position += 1
    }
    println(s"Non-essential matches present = $nonEssentialOnesCounter")
    interpolatedMatchesArrayBuilder.result()
  }
}
