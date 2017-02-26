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
      Files.readAllBytes(Paths.get("corpora", "enwik", "enwik7"))

    val minMatch = 2
    val maxMatch = 120

    val tarsaAcceptedBuilder = mutable.ArrayBuilder.make[Match.Packed]()
    tarsaAcceptedBuilder.sizeHint(120 * 1000 * 1000)
    var tarsaDiscardedCounter = 0

    StdIn.readLine("Press Enter to start processing...")

    Timed("TarsaMatchFinder.run") {
      TarsaMatchFinder.run(data,
        minMatch,
        maxMatch,
        tarsaAcceptedBuilder += _,
        _ => tarsaDiscardedCounter += 1)
    }

    val tarsaAcceptedArray = tarsaAcceptedBuilder.result()

    println(s"Tarsa accepted:  ${tarsaAcceptedArray.length}")
    println(s"Tarsa discarded: $tarsaDiscardedCounter")
  }
}
