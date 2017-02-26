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
package pl.tarsa.matchfinders

import java.nio.file.{Files, Paths}

import pl.tarsa.matchfinders.finders.TarsaMatchFinder
import pl.tarsa.matchfinders.model.Match
import pl.tarsa.util.Timed

import scala.collection.mutable
import scala.io.StdIn

object Runner {
  def main(args: Array[String]): Unit = {
    val data =
      Files.readAllBytes(Paths.get("corpora", "enwik", "enwik8"))

    val minMatch = 2
    val maxMatch = 120

    val tarsaAcceptedBuilder = mutable.ArrayBuilder.make[Match.Packed]()
    tarsaAcceptedBuilder.sizeHint(210 * 1000 * 1000)
    var tarsaDiscardedCounter = 0

    StdIn.readLine("Press Enter to start processing...")

    for (_ <- 0 to 0) {
      tarsaAcceptedBuilder.clear()
      tarsaDiscardedCounter = 0
      Timed("TarsaMatchFinder.run") {
        TarsaMatchFinder.run(data,
                             minMatch,
                             maxMatch,
                             tarsaAcceptedBuilder += _,
                             _ => tarsaDiscardedCounter += 1)
      }
    }

    val tarsaAcceptedArray = tarsaAcceptedBuilder.result()

    println(s"Tarsa accepted:  ${tarsaAcceptedArray.length}")
    println(s"Tarsa discarded: $tarsaDiscardedCounter")

    val lengthsHistogram = Array.ofDim[Int](5)
    for (packedMatch <- tarsaAcceptedArray) {
      val length = Match(packedMatch).length
      if (length <= 2) {
        lengthsHistogram(0) += 1
      } else if (length <= 5) {
        lengthsHistogram(length - 2) += 1
      } else {
        lengthsHistogram(4) += 1
      }
    }
    println("Accepted matches' lengths histogram:")
    println(s"  <= 2 - ${lengthsHistogram(0)}")
    println(s"  == 3 - ${lengthsHistogram(1)}")
    println(s"  == 4 - ${lengthsHistogram(2)}")
    println(s"  == 5 - ${lengthsHistogram(3)}")
    println(s"  >= 6 - ${lengthsHistogram(4)}")

    val offsetsHistogram = Array.ofDim[Int](10)
    for (packedMatch <- tarsaAcceptedArray) {
      val offset = Match(packedMatch).offset
      assert(offset > 0)
      val log10 = Math.log10(offset)
      val slot = Math.min(Math.ceil(log10).toInt, 9)
      offsetsHistogram(slot) += 1
    }
    println("Accepted matches' offsets histogram:")
    println(s"       offset == 1e0 - ${offsetsHistogram(0)}")
    offsetsHistogram.init.zipWithIndex.tail.foreach {
      case (counter, logarithm) =>
        println(s" 1e${logarithm - 1} < offset <= 1e$logarithm - $counter")
    }
    println(s" 1e8 < offset        - ${offsetsHistogram(9)}")

    var matchesWithAtLeastLength3AndOffset64KiB = 0
    for (packedMatch <- tarsaAcceptedArray) {
      val theMatch = Match(packedMatch)
      if (theMatch.length >= 3 && theMatch.offset >= (1 << 16)) {
        matchesWithAtLeastLength3AndOffset64KiB += 1
      }
    }
    println("Accepted matches with length >= 3 and offset >= 64KiB - " +
      s"$matchesWithAtLeastLength3AndOffset64KiB")
  }
}
