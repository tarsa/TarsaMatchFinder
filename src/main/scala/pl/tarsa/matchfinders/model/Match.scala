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
package pl.tarsa.matchfinders.model

import Match._

case class Match(packed: Packed) extends AnyVal {
  def position: Int =
    ((packed >> positionShift) & positionMask).toInt

  def length: Int =
    ((packed >> lengthShift) & lengthMask).toInt

  def offset: Int =
    ((packed >> offsetShift) & offsetMask).toInt

  def source: Int =
    position - offset

//  def target: Int =
//    position
}

object Match {
  type Packed = Long

  def Bottom: Packed =
    -1L

  def fromPositionLengthSource(position: Int,
                               length: Int,
                               source: Int): Match =
    fromPositionLengthOffset(position, length, position - source)

  def fromPositionLengthOffset(position: Int,
                               length: Int,
                               offset: Int): Match = {
    val packed =
      (position.toLong << positionShift) +
        (length.toLong << lengthShift) +
        (offset.toLong << offsetShift)
    Match(packed)
  }

  def positionBits: Int =
    28

  def lengthBits: Int =
    7

  def offsetBits: Int =
    28

  def positionMask: Int =
    (1 << positionBits) - 1

  def lengthMask: Int =
    (1 << lengthBits) - 1

  def offsetMask: Int =
    (1 << offsetBits) - 1

  def positionShift: Int =
    lengthShift + lengthBits

  def lengthShift: Int =
    offsetShift + offsetBits

  def offsetShift: Int =
    0
}
