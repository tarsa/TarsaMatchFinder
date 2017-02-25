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
      Files.readAllBytes(Paths.get("corpora", "enwik", "enwik5"))

    val minMatch = 2
    val maxMatch = 3

    val bruteForceAcceptedBuilder = mutable.ArrayBuilder.make[Match.Packed]()
    var bruteForceFilteredCounter = 0
    val tarsaAcceptedBuilder = mutable.ArrayBuilder.make[Match.Packed]()
    var tarsaFilteredCounter = 0

    timed("TarsaMatchFinder.run") {
      new TarsaMatchFinder()
        .run(data,
             minMatch,
             maxMatch,
             tarsaAcceptedBuilder += _,
             _ => tarsaFilteredCounter += 1)
    }

    timed("BruteForceMatchFinder.run") {
      new BruteForceMatchFinder().run(data,
                                      minMatch,
                                      maxMatch,
                                      bruteForceAcceptedBuilder += _,
                                      _ => bruteForceFilteredCounter += 1)
    }

    val bruteForceAcceptedArray = bruteForceAcceptedBuilder.result()
    val tarsaAcceptedArray = tarsaAcceptedBuilder.result()

    assert(isSorted(bruteForceAcceptedArray))
    java.util.Arrays.sort(tarsaAcceptedArray)

    tarsaAcceptedArray.take(1000).foreach { packedMatch =>
      val theMatch = Match(packedMatch)
      import theMatch._
      println(f"$offset%9d, $position%9d, $length%3d")
    }

    val onlyInBruteForceAccepted =
      (bruteForceAcceptedArray.toSet -- tarsaAcceptedArray.toSet).toArray
    java.util.Arrays.sort(onlyInBruteForceAccepted)

    if (onlyInBruteForceAccepted.nonEmpty) {
      onlyInBruteForceAccepted.take(1000).foreach { packedMatch =>
        println(s"Only in brute force accepted: ${Match(packedMatch)}")
      }
    }

    val onlyInTarsaAccepted =
      (tarsaAcceptedArray.toSet -- bruteForceAcceptedArray.toSet).toArray
    java.util.Arrays.sort(onlyInTarsaAccepted)

    if (onlyInTarsaAccepted.nonEmpty) {
      onlyInTarsaAccepted.take(1000).foreach { packedMatch =>
        println(s"Only in Tarsa accepted: ${Match(packedMatch)}")
      }
    }

    val bruteForceAcceptedForComparison =
      bruteForceAcceptedArray.toList.map { packedMatch =>
        if (java.util.Arrays.binarySearch(onlyInBruteForceAccepted,
                                          packedMatch) >= 0) {
          (packedMatch, "Accepted (BF) !!!")
        } else {
          (packedMatch, "Accepted (BF)")
        }
      }
    val tarsaAcceptedForComparison =
      tarsaAcceptedArray.toList.map { packedMatch =>
        if (java.util.Arrays.binarySearch(onlyInTarsaAccepted, packedMatch) >= 0) {
          (packedMatch, "Accepted (T)  %%%")
        } else {
          (packedMatch, "Accepted (T)")
        }
      }
    val bothForComparison =
      bruteForceAcceptedForComparison ++ tarsaAcceptedForComparison
    bothForComparison.toArray.sorted
      .take(2000)
      .foreach {
        case (packedMatch, desc) =>
          println(f"$desc%-20s ${Match(packedMatch)}")
      }

    println(s"Brute Force accepted: ${bruteForceAcceptedArray.length}")
    println(s"Brute Force filtered: $bruteForceFilteredCounter")

    println(s"Tarsa accepted: ${tarsaAcceptedArray.length}")
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

  def isSorted(matches: Array[Match.Packed]): Boolean =
    matches.sameElements(matches.sorted)
}
