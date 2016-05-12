/*
 * Copyright 2016 HM Revenue & Customs
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


package uk.gov.hmrc.pla.stub.rules

import java.util.Random

import uk.gov.hmrc.pla.stub.model.Protection
import uk.gov.hmrc.pla.stub.model.Protection.Type._
import uk.gov.hmrc.play.test.UnitSpec
import java.util.Random

object Generator {
  import uk.gov.hmrc.domain.Generator

  val rand = new Random()
  val ninoGenerator = new Generator(rand)

  def randomNino: String = ninoGenerator.nextNino.nino.replaceFirst("MA", "AA")
  def randomProtectionID = rand.nextLong
  def randomFP16ProtectionReference=("FP16" + Math.abs(rand.nextLong)).substring(0,9) + "C"
  def randomIP16ProtectionReference=("IP16" + Math.abs(rand.nextLong)).substring(0,9) + "B"
  def randomIP14ProtectionReference=("IP14" + Math.abs(rand.nextLong)).substring(0,9) + "A"
  def randomOlderProtectionReference=("A" +  Math.abs(rand.nextLong)).substring(0,5) + "A"
}

object Protections {

  import Generator._

  val openFP2016=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.FP2016.toString,
    status=Protection.Status.Open.toString,
    notificationId=Some(22),
    notificationMsg=None,
    protectionReference=Some(randomFP16ProtectionReference),
    version = 1)

  val openIP2016=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.IP2016.toString,
    status=Protection.Status.Open.toString,
    notificationId=Some(12),
    notificationMsg=None,
    protectionReference=Some(randomFP16ProtectionReference),
    version = 1)

  val openFP2014=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.FP2014.toString,
    status=Protection.Status.Open.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomFP16ProtectionReference),
    version = 1)


  val openIP2014=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.IP2014.toString,
    status=Protection.Status.Open.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomFP16ProtectionReference),
    version = 1)

  val openPrimary=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.Primary.toString,
    status=Protection.Status.Open.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomOlderProtectionReference),
    version = 1)

  val openFixed=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.Fixed.toString,
    status=Protection.Status.Open.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomOlderProtectionReference),
    version = 1)

  val openEnhanced=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.Enhanced.toString,
    status=Protection.Status.Open.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomOlderProtectionReference),
    version = 1)

  val dormantPrimary=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.Primary.toString,
    status=Protection.Status.Dormant.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomOlderProtectionReference),
    version = 1)

  val dormantEnhanced=Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.Enhanced.toString,
    status=Protection.Status.Dormant.toString,
    notificationId=None,
    notificationMsg=None,
    protectionReference=Some(randomOlderProtectionReference),
    version = 1)

  val rejected = Protection(
    nino=randomNino,
    protectionID=randomProtectionID,
    protectionType=Protection.Type.IP2016.toString,
    status=Protection.Status.Rejected.toString,
    notificationId=Some(21),
    notificationMsg=None,
    protectionReference=None,
    version = 1)
}

class FP2016ApplicationRulesSpec extends UnitSpec {

  import Protections._


  "An application for an FP2016 when no protections already exist for the individual" should {
    "return a notification ID of 22" in {
      FP2016ApplicationRules.check(List()) shouldBe 22
    }
  }

  "An application for an FP2016 when an open FP2016 already exists for the individual" should {
    "return a notification ID of 21" in {

      FP2016ApplicationRules.check(List(openFP2016)) shouldBe 21
    }
  }

  "An application for an FP2016 when an open Primary already exists for the individual" should {
    "return a notification ID of 18" in {
      val existing=openPrimary

      FP2016ApplicationRules.check(List(openPrimary)) shouldBe 18
    }
  }

  "An application for an FP2016 when an open Enhanced already exists for the individual" should {
    "return a notification ID of 17" in {

      FP2016ApplicationRules.check(List(openEnhanced)) shouldBe 17
    }
  }

  "An application for an FP2016 when an open Fixed already exists for the individual" should {
    "return a notification ID of 19" in {
      FP2016ApplicationRules.check(List(openFixed)) shouldBe 19
    }
  }

  "An application for an FP2016 when an open FP2014 already exists for the individual" should {
    "return a notification ID of 20" in {
      FP2016ApplicationRules.check(List(openFP2014)) shouldBe 20
    }
  }

  "An application for an FP2016 when an open IP2014 already exists for the individual" should {
    "return a notification ID of 23" in {
      FP2016ApplicationRules.check(List(openIP2014)) shouldBe 23
    }
  }

  "An application for an FP2016 when an open IP2016 already exists for the individual" should {
    "return a notification ID of 24" in {
      FP2016ApplicationRules.check(List(openIP2016)) shouldBe 24
    }
  }

  "An application for an FP2016 when only a dormant Primary protection already exists for the individual" should {
    "return a notification ID of 18" in {
      FP2016ApplicationRules.check(List(dormantPrimary)) shouldBe 18
    }
  }

  "An application for an FP2016 when only a dormant non-Primary protection already exists for the individual" should {
    "return a notification ID of 22" in {
      FP2016ApplicationRules.check(List(dormantEnhanced)) shouldBe 22
    }
  }

  "An application for an FP2016 when only a rejected protection already exists for the individual" should {
    "return a notification ID of 22" in {
      FP2016ApplicationRules.check(List(rejected)) shouldBe 22
    }
  }

  "An application for an FP2016 when a rejected protection plus an open FP2016 already exists for the individual" should {
    "return a notification ID of 21" in {
      FP2016ApplicationRules.check(List(rejected, openFP2016)) shouldBe 21
    }
  }

  "An application for an FP2016 when a dormant non-Primary protection plus an open FP2016 already exists for the individual" should {
    "return a notification ID of 21" in {
      FP2016ApplicationRules.check(List(dormantEnhanced, openFP2016)) shouldBe 21
    }
  }

  "An application for an FP2016 when a rejected application, a dormant non-Primary protection plus an open FP2016 already exists for the individual" should {
    "return a notification ID of 21" in {
      FP2016ApplicationRules.check(List(rejected, dormantEnhanced, openFP2016)) shouldBe 21
    }
  }

}
