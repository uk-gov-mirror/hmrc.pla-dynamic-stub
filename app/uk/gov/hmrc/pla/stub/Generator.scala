/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pla.stub.model

import org.scalacheck._
import uk.gov.hmrc.smartstub._
import cats.implicits._
import org.scalacheck.support.cats._
import java.time.format.DateTimeFormatter._
import java.time.{LocalDate,LocalTime}



object Generator {

  /**
    * "^[1-9A][0-9]{6}[ABCDEFHXJKLMNYPQRSTZW]|(IP14|IP16|FP16)[0-9]{10}[ABCDEFGHJKLMNPRSTXYZ]$"
    */
  val refGen: Gen[String] = {
    val refOne = List(
      Gen.oneOf("A" :: {1 to 9}.map{_.toString}.toList),
      pattern"999999".gen,
      Gen.oneOf("ABCDEFHXJKLMNYPQRSTZW".toList)
    ).sequence

    val refTwo = List(
      Gen.oneOf("IP14", "IP16", "FP16"),
      pattern"9999999999".gen,
      Gen.oneOf("ABCDEFGHJKLMNPRSTXYZ".toList)
    ).sequence

    Gen.oneOf ( refOne, refTwo ).map{_.mkString}
  }

  val genTime: Gen[LocalTime] = Gen.choose(0, 24*60*60).map{
    x => LocalTime.parse(
      {BigInt(1000000000L) * x}.toString,
      ofPattern("N")
    )
  }

  val genMoney: Gen[Option[Double]] =
    Gen.choose(1, 1000000000).map{_.toDouble / 100}.sometimes


  val pensionDebitGen: Gen[PensionDebit] = {
    Gen.choose(1, 1000000).map{_.toDouble} |@|
    Gen.date(2014, 2017).map { _.format(ISO_LOCAL_DATE) }
  }.map(PensionDebit.apply)

  def genProtection(nino: String): Gen[Protection] =
    Gen.choose(1,5) flatMap (genProtection(nino,_))

  def genProtection(nino: String, version: Int): Gen[Protection] = {
    Gen.const(nino) |@|   // nino
    Gen.choose(1,4).map{_.toLong} |@|       // id
    Gen.const(version) |@|                  // version
    Gen.choose(1,7) |@|                     // `type`
    Gen.choose(1,6) |@|                     // status
    Gen.choose(1,47).map(_.toShort).
      sometimes |@|                         // notificationID
    Gen.alphaStr.sometimes |@|              // notificationMsg
    refGen.almostAlways |@|                 // protectionReference
    Gen.date(2014, 2017).map {
      _.format(ISO_LOCAL_DATE)
    }.sometimes |@|                         // certificateDate
    genTime.map {
      _.format(ISO_LOCAL_TIME)
    }.sometimes |@|                         // certificateTime
    genMoney |@|                            // relevantAmount
    genMoney |@|                            // protectedAmount
    genMoney |@|                            // preADayPensionInPayment
    genMoney |@|                            // postADayBCE
    genMoney |@|                            // uncrystallisedRights
    genMoney |@|                            // nonUKRights
    genMoney |@|                            // pensionDebiitEnteredAmount
    genMoney |@|                            // pensionDebitStartDate
    genMoney |@|                            // pensionDebitTotalAmount
    Gen.listOf(pensionDebitGen).
      sometimes |@|                         // pensionDebits
    Gen.listOfN(version - 1, Gen.alphaStr).
      sometimes                             // previousVersions
  }.map(Protection.apply)
}
