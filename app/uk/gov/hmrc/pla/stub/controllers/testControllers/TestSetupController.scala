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

package uk.gov.hmrc.pla.stub.controllers.testControllers

import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.pla.stub.repository.{ProtectionRepository, MongoProtectionRepository}
import uk.gov.hmrc.pla.stub.model.Protection
import uk.gov.hmrc.pla.stub.model.Error

import play.api.mvc._
import play.api.libs.json._

import scala.Error
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TestSetupController extends TestSetupController {
    
  override val protectionRepository = MongoProtectionRepository()
}

trait TestSetupController extends BaseController {
    
    val protectionRepository: ProtectionRepository


  /**
    * Stub-only convenience operation to add a protection to test data
    * @return
    */
  def insertProtection() = Action.async (BodyParsers.parse.json) { implicit request =>
    val protectionJs = request.body.validate[Protection]
    protectionJs.fold(
      errors => Future.successful(BadRequest(Json.toJson(Error(message="body failed validation with errors: " + errors)))),
      protection =>
        protectionRepository.insert(protection)
          .map { _ => Ok }
          .recover { case exception => Results.InternalServerError(exception.toString) }
    )
  }

  /**
    * Stub-only convenience operation to tear down test data
    * @return
    */
  def removeAllProtections() = Action.async { implicit request =>
    protectionRepository.removeAllProtections()
    Future.successful(Ok)
  }

  /**
    * Stub-only convenience operation to tear down test data for a given NINO
    *
    * @param nino
    * @return
    */
  def removeProtections(nino: String) = Action.async { implicit request =>
    protectionRepository.removeByNino(nino: String)
    Future.successful(Ok)
  }

  /**
    * Stub-only convenience operation to tear down test data for a specified protection
    *
    * @param nino
    * @param protectionId
    * @return
    */
  def removeProtection(nino: String, protectionId: Long) = Action.async { implicit request =>
    protectionRepository.removeByNinoAndProtectionID(nino: String, protectionId)
    Future.successful(Ok)
  }

}