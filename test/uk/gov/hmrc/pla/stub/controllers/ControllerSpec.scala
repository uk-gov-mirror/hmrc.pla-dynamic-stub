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

package uk.gov.hmrc.pla.stub.controllers

import uk.gov.hmrc.play.test.UnitSpec
import java.util.Random

import play.api.libs.json.{JsSuccess, _}
import uk.gov.hmrc.pla.stub.model._


object TestData {
  val validFP2016CeateRequest = CreateLTAProtectionRequest(
    nino = Generator.randomNino,
    protection = ProtectionApplicationTestData.Fp2016
  )
  val authHeader = "Authorization" -> "Bearer: abcdef12345678901234567890"
  val envHeader = "Environment" -> "IST0"

  val validHeadersExample = Map(authHeader,envHeader)
  val noAuthHeadersExample = Map(envHeader)
  val noEnvHeadersExample = Map(authHeader)
  val emptyHeadersExample = Map[String,String]()
  val invalidRequestJsError = JsError("invalid request body")
}
class PLAStubControllerSpec extends UnitSpec {
  "The validation results for a valid CreateProtectionRequest and valid headers should be a success" should {
    "return a valid request object" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.validHeadersExample, JsSuccess(TestData.validFP2016CeateRequest))
      requestJs.isSuccess shouldBe true
      val request = requestJs.get
      request shouldEqual TestData.validFP2016CeateRequest
    }
  }

  "The validation results for an invalid CreateProtectionRequest and valid headers" should {
    "return a failure with one error" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.validHeadersExample, TestData.invalidRequestJsError)
      requestJs.isSuccess shouldBe false
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 1
    }
  }

  "The validation results for a valid CreateProtectionRequest and missing Environment header" should {
    "return a failure with one error" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.noEnvHeadersExample, JsSuccess(TestData.validFP2016CeateRequest))
      requestJs.isSuccess shouldBe false
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 1
    }
  }

  "The validation results for a valid CreateProtectionRequest and missing Authorization header" should {
    "return a failure with one error" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.noAuthHeadersExample, JsSuccess(TestData.validFP2016CeateRequest))
      requestJs.isSuccess shouldBe false
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 1
    }
  }


  "The validation results for a valid CreateProtectionRequest and missing Authorization and Environment headers" should {
    "return a failure with two validation errosr" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.emptyHeadersExample, JsSuccess(TestData.validFP2016CeateRequest))
      requestJs.isSuccess shouldBe false
      println("RESULT ==> " + requestJs)
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 2
    }
  }

  "The validation results for an invalid CreateProtectionRequest and missing Authorization and Environment headers" should {
    "return a failure with three validation errosr" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.emptyHeadersExample,  TestData.invalidRequestJsError)
      requestJs.isSuccess shouldBe false
      println("RESULT ==> " + requestJs)
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 3
    }
  }
}
