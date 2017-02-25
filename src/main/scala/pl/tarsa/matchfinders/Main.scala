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
import java.nio.{ByteBuffer, ByteOrder}

import pl.tarsa.matchfinders.finders.{
  BruteForceMatchFinder,
  MatchFinder,
  TarsaMatchFinder
}
import pl.tarsa.matchfinders.interpolation.Interpolator
import pl.tarsa.matchfinders.model.Match
import pl.tarsa.matchfinders.verification.Verifier

import scala.collection.mutable

object Main {
  private val compactedMatchesFileMagicNumber = 3463562352346342432l
  private val interpolatedMatchesFileMagicNumber = 3765472453426534653l
  private val headerSize = 8 + 4 + 2 + 2
  private val serializedMatchSize = 4 * 4

  case class Header(magicNumber: Long,
                    inputSize: Int,
                    minMatch: Short,
                    maxMatch: Short)

  def main(args: Array[String]): Unit = {
    args.toSeq match {
      case Seq("find-matches", input, finder, min, max, compacted) =>
        findMatches(input, finder, min, max, compacted)
      case Seq("interpolate", input, compacted, interpolated) =>
        interpolate(input, compacted, interpolated)
      case Seq("verify", input, interpolated) =>
        verify(input, interpolated)
      case Seq("help") =>
        printHelp(None)
      case Seq() =>
        printHelp(Some("Please specify a command"))
      case _ =>
        printHelp(Some("Error: cannot parse command"))
    }
  }

  def findMatches(inputFileName: String,
                  finderName: String,
                  minString: String,
                  maxString: String,
                  compactedFileName: String): Unit = {
    val input = Files.readAllBytes(Paths.get(inputFileName))
    val finder: MatchFinder =
      finderName match {
        case "bfmf" =>
          new BruteForceMatchFinder()
        case "tmf" =>
          new TarsaMatchFinder()
      }
    val minMatch = minString.toInt
    val maxMatch = maxString.toInt
    assert(1 <= minMatch && minMatch <= maxMatch && maxMatch <= 120)
    val filteredMatchesBuffer = mutable.Buffer.empty[Match]
    finder.run(input, minMatch, maxMatch, filteredMatchesBuffer += _, _ => ())
    val compactedMatchesDataArray = Array.ofDim[Byte](
      headerSize + filteredMatchesBuffer.size * serializedMatchSize)
    val compactedMatchesDataBuffer =
      ByteBuffer.wrap(compactedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    writeHeader(Header(compactedMatchesFileMagicNumber,
                       input.length,
                       minMatch.toShort,
                       maxMatch.toShort),
                compactedMatchesDataBuffer)
    filteredMatchesBuffer.toArray
      .sortBy(_.target)
      .foreach(writeMatch(_, compactedMatchesDataBuffer))
    println(s"Filtered matches written = ${filteredMatchesBuffer.size}")
    Files.write(Paths.get(compactedFileName), compactedMatchesDataArray)
  }

  def interpolate(inputFileName: String,
                  compactedFileName: String,
                  interpolatedFileName: String): Unit = {
    val input = Files.readAllBytes(Paths.get(inputFileName))
    val compactedMatchesDataArray =
      Files.readAllBytes(Paths.get(compactedFileName))
    val compactedMatchesDataBuffer =
      ByteBuffer.wrap(compactedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    val Header(magicNumber, inputSize, minMatch, maxMatch) =
      readHeader(compactedMatchesDataBuffer)
    assert(magicNumber == compactedMatchesFileMagicNumber)
    assert(inputSize == input.length)
    val filteredMatchesBuffer = mutable.ArrayBuffer.empty[Match]
    while (compactedMatchesDataBuffer.hasRemaining) {
      filteredMatchesBuffer += readMatch(compactedMatchesDataBuffer)
    }
    val interpolatedMatches =
      new Interpolator().run(input, minMatch, maxMatch, filteredMatchesBuffer)
    val interpolatedMatchesDataArray = Array.ofDim[Byte](
      headerSize + interpolatedMatches.size * serializedMatchSize)
    val interpolatedMatchesDataBuffer =
      ByteBuffer.wrap(interpolatedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    writeHeader(Header(interpolatedMatchesFileMagicNumber,
                       inputSize,
                       minMatch,
                       maxMatch),
                interpolatedMatchesDataBuffer)
    interpolatedMatches.foreach(writeMatch(_, interpolatedMatchesDataBuffer))
    Files.write(Paths.get(interpolatedFileName), interpolatedMatchesDataArray)
  }

  def verify(inputFileName: String, interpolatedFileName: String): Unit = {
    val input = Files.readAllBytes(Paths.get(inputFileName))
    val interpolatedMatchesDataArray =
      Files.readAllBytes(Paths.get(interpolatedFileName))
    val interpolatedMatchesDataBuffer =
      ByteBuffer.wrap(interpolatedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    val Header(magicNumber, inputSize, minMatch, maxMatch) =
      readHeader(interpolatedMatchesDataBuffer)
    assert(magicNumber == interpolatedMatchesFileMagicNumber)
    assert(inputSize == input.length)
    val interpolatedMatchesBuffer = mutable.ArrayBuffer.empty[Match]
    while (interpolatedMatchesDataBuffer.hasRemaining) {
      interpolatedMatchesBuffer += readMatch(interpolatedMatchesDataBuffer)
    }
    val verificationOk =
      new Verifier().run(input, minMatch, maxMatch, interpolatedMatchesBuffer)
    if (verificationOk) {
      println("Verification OK")
    } else {
      println("Verification failed")
    }
  }

  def readHeader(dataBuffer: ByteBuffer): Header = {
    Header(dataBuffer.getLong,
           dataBuffer.getInt,
           dataBuffer.getShort,
           dataBuffer.getShort)
  }

  def writeHeader(header: Header, dataBuffer: ByteBuffer): Unit = {
    dataBuffer.putLong(header.magicNumber)
    dataBuffer.putInt(header.inputSize)
    dataBuffer.putShort(header.minMatch)
    dataBuffer.putShort(header.maxMatch)
  }

  def readMatch(dataBuffer: ByteBuffer): Match = {
    val result =
      Match(dataBuffer.getInt(), dataBuffer.getInt, dataBuffer.getInt)
    dataBuffer.getInt
    result
  }

  def writeMatch(m: Match, dataBuffer: ByteBuffer): Unit = {
    dataBuffer.putInt(m.source)
    dataBuffer.putInt(m.target)
    dataBuffer.putInt(m.length)
    dataBuffer.putInt(0)
  }

  def printHelp(extraMessageOpt: Option[String]): Unit = {
    extraMessageOpt.foreach(println)
    println(
      """ Available commands (case sensitive):
        |  help
        |    displays this help
        |  find-matches <input> <finder> <min> <max> <compacted>
        |    finds matches in input file and stores the essential ones
        |    input: input file with original data
        |    finder: match finder, one of:
        |      bfmf: brute force match finder
        |      tmf: Tarsa match finder
        |    min: minimum match size, min >= 1, min <= max
        |    max: maximum match size, max >= min, max <= 120
        |    compacted: file where filtered matches will be written
        |  interpolate <input> <compacted> <interpolated>
        |    reconstructs full set of matches from essential ones
        |    input: input file with original data
        |    compacted: file with filtered matches
        |    interpolated: file where full set of matches will be written
        |  verify <input> <interpolated>
        |    uses brute force match finder to verify presence of all matches
        |    input: input file with original data
        |    interpolated: file with full set of matches
      """.stripMargin
    )
  }
}
