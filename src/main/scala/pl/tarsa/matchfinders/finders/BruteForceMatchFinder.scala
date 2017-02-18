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

class BruteForceMatchFinder extends MatchFinder {
  override def run(data: Array[Byte],
                   minMatch: Int,
                   maxMatch: Int,
                   onAccepted: Match => Unit,
                   onFiltered: Match => Unit): Unit = {
    // variables
    val inheritedOffsets = Array.ofDim[Int](maxMatch + 1)
    val currentOffsets = Array.ofDim[Int](maxMatch + 1)
    var inheritedMaxMatch = 0
    var currentMaxMatch = 0
    var position = 0
    var matchLength = 0
    var offset = 0
    // main loop
    position = 1
    while (position < data.length) {
      // inheriting matches
      matchLength = 2
      while (matchLength <= currentMaxMatch) {
        inheritedOffsets(matchLength - 1) = currentOffsets(matchLength)
        matchLength += 1
      }
      inheritedMaxMatch = currentMaxMatch - 1
      // trying to extend longest match by one byte
      if (currentMaxMatch == maxMatch && position + maxMatch < data.length &&
          data(position - currentOffsets(maxMatch) + maxMatch - 1) ==
            data(position + maxMatch - 1)) {
        inheritedMaxMatch = maxMatch
        inheritedOffsets(maxMatch) = currentOffsets(maxMatch)
      }
      // clearing current matches
      currentMaxMatch = 0
      // collecting matches for current position
      offset = 1
      while (offset <= position && currentMaxMatch < maxMatch) {
        matchLength =
          computeMatchLength(data, position - offset, position, maxMatch)
        while (currentMaxMatch < matchLength) {
          currentMaxMatch += 1
          currentOffsets(currentMaxMatch) = offset
        }
        offset += 1
      }
      // filtering and outputting matches
      matchLength = currentMaxMatch
      while (matchLength >= minMatch) {
        val currentIsInherited = matchLength <= inheritedMaxMatch &&
            inheritedOffsets(matchLength) == currentOffsets(matchLength)
        val higherHasSameOffset = matchLength < currentMaxMatch &&
            currentOffsets(matchLength) == currentOffsets(matchLength + 1)
        val theMatch =
          Match(position - currentOffsets(matchLength), position, matchLength)
        if (currentIsInherited || higherHasSameOffset) {
          onFiltered(theMatch)
        } else {
          onAccepted(theMatch)
        }
        matchLength -= 1
      }
      // advance to next iteration
      position += 1
    }
  }

  private def computeMatchLength(data: Array[Byte],
                                 sourcePos: Int,
                                 targetPos: Int,
                                 maxMatch: Int): Int = {
    var validatedMatchLength = 0
    while (sourcePos + validatedMatchLength < data.length &&
           targetPos + validatedMatchLength < data.length &&
           (data(sourcePos + validatedMatchLength) ==
             data(targetPos + validatedMatchLength)) &&
           validatedMatchLength < maxMatch) {
      validatedMatchLength += 1
    }
    validatedMatchLength
  }
}
