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

import pl.tarsa.matchfinders.finders.{BruteForceMatchFinder, TarsaMatchFinder}
import pl.tarsa.matchfinders.model.Match

import scala.collection.mutable

object Checker {
  def main(args: Array[String]): Unit = {
    val data =
      Files.readAllBytes(Paths.get("corpora", "enwik", "enwik6"))

    val minMatch = 2
    val maxMatch = 3

    val bruteForceAccepted = mutable.Buffer.empty[Match]
    var bruteForceFilteredCounter = 0
    val tarsaAccepted = mutable.Buffer.empty[Match]
    var tarsaFilteredCounter = 0

    timed("TarsaMatchFinder.run") {
      new TarsaMatchFinder()
        .run(data,
             minMatch,
             maxMatch,
             tarsaAccepted += _,
             _ => tarsaFilteredCounter += 1)
    }

    timed("BruteForceMatchFinder.run") {
      new BruteForceMatchFinder().run(data,
                                      minMatch,
                                      maxMatch,
                                      bruteForceAccepted += _,
                                      _ => bruteForceFilteredCounter += 1)
    }

//    (bruteForceAccepted.toList.map(("Accepted (BF)", _)) ++
//      tarsaAccepted.toList.map(("Accepted (T)", _))).toArray
//      .sortBy { case (desc, m) => (m.target, m.length, desc) }
//      .foreach { case (desc, m) => println(f"$desc%-20s $m") }

//    tarsaAccepted.toArray.sortBy(m => (m.target, m.length)).foreach { m =>
//      println(f"${m.target - m.source}%7d, ${m.target}%7d, ${m.length}%3d")
//    }

    val onlyInBruteForceAccepted =
      (bruteForceAccepted.toSet -- tarsaAccepted.toSet).toList

    if (onlyInBruteForceAccepted.nonEmpty) {
      println("Only in brute force accepted:")
      onlyInBruteForceAccepted
        .sortBy(m => (m.target, m.length))
        .foreach(println)
    }

    val onlyInTarsaAccepted =
      (tarsaAccepted.toSet -- bruteForceAccepted.toSet).toList

    if (onlyInTarsaAccepted.nonEmpty) {
      println("Only in Tarsa accepted:")
      onlyInTarsaAccepted
        .sortBy(m => (m.target, m.length))
        .foreach(println)
    }

    println(s"Brute Force accepted: ${bruteForceAccepted.size}")
    println(s"Brute Force filtered: $bruteForceFilteredCounter")

    println(s"Tarsa accepted: ${tarsaAccepted.size}")
    println(s"Tarsa filtered: $tarsaFilteredCounter")
  }

  def echo(string: String): Unit =
    () // println(string)

  def timed[T](description: String)(action: => T): T = {
    val startTime = System.currentTimeMillis()
    val result = action
    val endTime = System.currentTimeMillis()
    println(s"$description took ${endTime - startTime} ms")
    result
  }
}
