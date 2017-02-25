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
package pl.tarsa.matchfinders.verification

import pl.tarsa.matchfinders.model.Match

class Verifier {
  def run(data: Array[Byte],
          minMatch: Int,
          maxMatch: Int,
          interpolatedMatches: Array[Match.Packed]): Boolean = {
    // variables
    var matchIndex = 0
    var currentMaxMatch = 0
    var position = 0
    var matchLength = 0
    var offset = 0
    // main loop
    position = 1
    while (position < data.length) {
      currentMaxMatch = 0
      offset = 1
      while (offset <= position && currentMaxMatch < maxMatch) {
        matchLength =
          computeMatchLength(data, position - offset, position, maxMatch)
        while (currentMaxMatch < matchLength) {
          currentMaxMatch += 1
          if (currentMaxMatch >= minMatch) {
            val inputMatch = Match(interpolatedMatches(matchIndex))
            matchIndex += 1
            assert(inputMatch.position == position)
            assert(inputMatch.length == currentMaxMatch)
            assert(inputMatch.offset == offset)
          }
        }
        offset += 1
      }
      // advance to next iteration
      position += 1
    }
    matchIndex == interpolatedMatches.length
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
