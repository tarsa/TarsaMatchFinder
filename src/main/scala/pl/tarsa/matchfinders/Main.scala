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
  private val essentialMatchesFileMagicNumber = 3463562352346342432l
  private val interpolatedMatchesFileMagicNumber = 3765472453426534653l
  private val headerSize = 8 + 4 + 2 + 2
  private val serializedMatchSize = 4 * 4

  case class Header(magicNumber: Long,
                    inputSize: Int,
                    minMatch: Short,
                    maxMatch: Short)

  def main(args: Array[String]): Unit = {
    args.toSeq match {
      case Seq("find-matches", input, finder, min, max, essential) =>
        findMatches(input, finder, min, max, essential)
      case Seq("interpolate", essential, interpolated) =>
        interpolate(essential, interpolated)
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

  def findMatches(inputDataFileName: String,
                  finderName: String,
                  minString: String,
                  maxString: String,
                  essentialMatchesFileName: String): Unit = {
    val inputData = Files.readAllBytes(Paths.get(inputDataFileName))
    val finder: MatchFinder =
      finderName match {
        case "bfmf" =>
          BruteForceMatchFinder
        case "tmf" =>
          TarsaMatchFinder
      }
    val minMatch = minString.toInt
    val maxMatch = maxString.toInt
    assert(1 <= minMatch && minMatch <= maxMatch && maxMatch <= 120)
    val essentialMatchesArrayBuilder =
      mutable.ArrayBuilder.make[Match.Packed]()
    var discardedMatchesCounter = 0
    finder.run(inputData,
               minMatch,
               maxMatch,
               essentialMatchesArrayBuilder += _,
               _ => discardedMatchesCounter += 1)
    val essentialMatchesArray = essentialMatchesArrayBuilder.result()
    val essentialMatchesNumber = essentialMatchesArray.length
    val essentialMatchesDataArray = Array.ofDim[Byte](
      headerSize + essentialMatchesNumber * serializedMatchSize)
    val essentialMatchesDataBuffer =
      ByteBuffer.wrap(essentialMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    writeHeader(Header(essentialMatchesFileMagicNumber,
                       inputData.length,
                       minMatch.toShort,
                       maxMatch.toShort),
                essentialMatchesDataBuffer)
    java.util.Arrays.sort(essentialMatchesArray)
    essentialMatchesArray.foreach { packedMatch =>
      writeMatch(Match(packedMatch), essentialMatchesDataBuffer)
    }
    println(s"Essential matches written       = $essentialMatchesNumber")
    println(s"Non-essential matches discarded = $discardedMatchesCounter")
    Files.write(Paths.get(essentialMatchesFileName), essentialMatchesDataArray)
  }

  def interpolate(essentialMatchesFileName: String,
                  interpolatedMatchesFileName: String): Unit = {
    val essentialMatchesDataArray =
      Files.readAllBytes(Paths.get(essentialMatchesFileName))
    val essentialMatchesDataBuffer =
      ByteBuffer.wrap(essentialMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    val Header(magicNumber, inputSize, minMatch, maxMatch) =
      readHeader(essentialMatchesDataBuffer)
    assert(magicNumber == essentialMatchesFileMagicNumber)
    val essentialMatchesArrayBuilder =
      mutable.ArrayBuilder.make[Match.Packed]()
    while (essentialMatchesDataBuffer.hasRemaining) {
      essentialMatchesArrayBuilder +=
        readMatch(essentialMatchesDataBuffer).packed
    }
    val essentialMatchesArray = essentialMatchesArrayBuilder.result()
    val interpolatedMatchesArray = new Interpolator()
      .run(inputSize, minMatch, maxMatch, essentialMatchesArray)
    val interpolatedMatchesNumber = interpolatedMatchesArray.length
    val interpolatedMatchesDataArray = Array.ofDim[Byte](
      headerSize + interpolatedMatchesNumber * serializedMatchSize)
    val interpolatedMatchesDataBuffer =
      ByteBuffer.wrap(interpolatedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    writeHeader(Header(interpolatedMatchesFileMagicNumber,
                       inputSize,
                       minMatch,
                       maxMatch),
                interpolatedMatchesDataBuffer)
    interpolatedMatchesArray.foreach { packedMatch =>
      writeMatch(Match(packedMatch), interpolatedMatchesDataBuffer)
    }
    println(s"Interpolated matches written = $interpolatedMatchesNumber")
    Files.write(Paths.get(interpolatedMatchesFileName),
                interpolatedMatchesDataArray)
  }

  def verify(inputDataFileName: String,
             interpolatedMatchesFileName: String): Unit = {
    val input = Files.readAllBytes(Paths.get(inputDataFileName))
    val interpolatedMatchesDataArray =
      Files.readAllBytes(Paths.get(interpolatedMatchesFileName))
    val interpolatedMatchesDataBuffer =
      ByteBuffer.wrap(interpolatedMatchesDataArray).order(ByteOrder.BIG_ENDIAN)
    val Header(magicNumber, inputSize, minMatch, maxMatch) =
      readHeader(interpolatedMatchesDataBuffer)
    assert(magicNumber == interpolatedMatchesFileMagicNumber)
    assert(inputSize == input.length)
    val interpolatedMatchesArrayBuilder =
      mutable.ArrayBuilder.make[Match.Packed]()
    while (interpolatedMatchesDataBuffer.hasRemaining) {
      interpolatedMatchesArrayBuilder +=
        readMatch(interpolatedMatchesDataBuffer).packed
    }
    val interpolatedMatchesArray = interpolatedMatchesArrayBuilder.result()
    val verificationOk =
      new Verifier().run(input, minMatch, maxMatch, interpolatedMatchesArray)
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
      Match.fromPositionLengthOffset(dataBuffer.getInt(),
                                     dataBuffer.getInt(),
                                     dataBuffer.getInt())
    dataBuffer.getInt()
    result
  }

  def writeMatch(m: Match, dataBuffer: ByteBuffer): Unit = {
    dataBuffer.putInt(m.position)
    dataBuffer.putInt(m.length)
    dataBuffer.putInt(m.offset)
    dataBuffer.putInt(0)
  }

  def printHelp(extraMessageOpt: Option[String]): Unit = {
    extraMessageOpt.foreach(println)
    println(
      """ Available commands (case sensitive):
        |  help
        |    displays this help
        |  find-matches <input> <finder> <min> <max> <essential>
        |    finds all optimal matches in input and stores the essential ones
        |    input: input file with original data
        |    finder: match finder, one of:
        |      bfmf: brute force match finder
        |      tmf: Tarsa match finder
        |    min: minimum match size, min >= 1, min <= max
        |    max: maximum match size, max >= min, max <= 120
        |    essential: file to store essential matches
        |  interpolate <essential> <interpolated>
        |    reconstructs full set of optimal matches from essential ones
        |    essential: file with essential matches
        |    interpolated: file to store full set of optimal matches
        |  verify <input> <interpolated>
        |    uses brute force search to verify presence of all optimal matches
        |    input: input file with original data
        |    interpolated: file with full set of optimal matches
      """.stripMargin
    )
  }
}
