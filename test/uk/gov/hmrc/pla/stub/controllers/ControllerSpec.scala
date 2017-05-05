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

package uk.gov.hmrc.pla.stub.controllers

import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pla.stub.model._
import uk.gov.hmrc.play.test.UnitSpec

object TestData {
  val validFP2016CeateRequest = CreateLTAProtectionRequest(
    nino = Generator.randomNino,
    protection = ProtectionApplicationTestData.Fp2016
  )
  val authHeader = "Authorization" -> "Bearer: abcdef12345678901234567890"
  val envHeader = "Environment" -> "IST0"

  val validHeadersExample = Map(authHeader, envHeader)
  val noAuthHeadersExample = Map(envHeader)
  val noEnvHeadersExample = Map(authHeader)
  val emptyHeadersExample = Map[String, String]()
  val invalidRequestJsError = JsError("invalid request body")

  val validResponse = "\"pensionSchemeAdministratorCheckReference\":\"PSA12345678A\",\"ltaType\":7,\"psaCheckResult\":1,\"protectedAmount\":25000"
  val notFoundResponse = "\"reason\":\"Resource not found\""
}

class PLAStubControllerSpec extends UnitSpec with OneAppPerSuite {
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
    "return a failure with three validation errors" in {
      val requestJs = ControllerHelper.addExtraRequestHeaderChecks(TestData.emptyHeadersExample, TestData.invalidRequestJsError)
      requestJs.isSuccess shouldBe false
      println("RESULT ==> " + requestJs)
      requestJs.asInstanceOf[JsError].errors.seq.size shouldBe 1
      requestJs.asInstanceOf[JsError].errors.seq.head._2.size shouldBe 3
    }
  }

  "PSA Lookup" should {
    "return a 403 Forbidden with empty body when provided no environment header" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678A", "IP141000000000A").apply(FakeRequest())
      status(result) shouldBe FORBIDDEN
      contentAsString(result) shouldBe ""
    }

    "return a 401 Unauthorised with body when provided no auth header" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678A", "IP141000000000A").apply(FakeRequest().withHeaders(TestData.envHeader))
      status(result) shouldBe UNAUTHORIZED
      contentAsString(result) should include("Required OAuth credentials not provided")
    }

    "return a 400 BadRequest with body when provided invalid psa and lta references" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("", "").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      val error = "Your submission contains one or more errors. Failed Parameter(s) - [pensionSchemeAdministratorCheckReference,lifetimeAllowanceReference]"
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(error)
    }

    "return a 400 BadRequest with body when provided invalid psaReference" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("", "IP141000000000A").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      val error = "Your submission contains one or more errors. Failed Parameter(s) - [pensionSchemeAdministratorCheckReference]"
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(error)
    }

    "return a 400 BadRequest with body when provided invalid ltaReference" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678A", "").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      val error = "Your submission contains one or more errors. Failed Parameter(s) - [lifetimeAllowanceReference]"
      status(result) shouldBe BAD_REQUEST
      contentAsString(result) should include(error)
    }

    "return a 404 with body when provided psa reference ending in Z" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678Z", "IP141000000000A").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      status(result) shouldBe NOT_FOUND
      contentAsString(result) should include(TestData.notFoundResponse)
    }

    "return a 404 with body when provided lta reference ending in Z" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678A", "IP141000000000Z").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      status(result) shouldBe NOT_FOUND
      contentAsString(result) should include(TestData.notFoundResponse)
    }

    "return a 200 with body when provided valid references" in {
      val controller = PLAStubController
      val result = controller.updatedPSALookup("PSA12345678A", "IP141000000000A").apply(FakeRequest().withHeaders(TestData.envHeader, TestData.authHeader))
      status(result) shouldBe OK
      contentAsString(result) should include(TestData.validResponse)
    }
  }
}
