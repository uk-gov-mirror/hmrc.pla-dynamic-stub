/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.pla.stub.repository.{ExceptionTriggerRepository, MongoExceptionTriggerRepository, MongoProtectionRepository, ProtectionRepository}
import uk.gov.hmrc.pla.stub.model.{Error, ExceptionTrigger, Generator, Protection}
import play.api.mvc._
import play.api.libs.json._
import uk.gov.hmrc.pla.stub.services.PLAProtectionService
import uk.gov.hmrc.pla.stub.services.PLAProtectionService.protectionsStore

import scala.Error
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object TestSetupController extends TestSetupController {
    
  override val exceptionTriggerRepository = MongoExceptionTriggerRepository()
}

trait TestSetupController extends BaseController {
    
    val exceptionTriggerRepository: ExceptionTriggerRepository

  /**
    * Stub-only convenience operation to add a protection to test data
    * @return
    */
  def insertProtection(): Action[JsValue] = Action.async (BodyParsers.parse.json) { implicit request =>
    val protectionJs = request.body.validate[Protection]
    protectionJs.fold(
      errors => Future.successful(BadRequest(Json.toJson(Error(message="body failed validation with errors: " + errors)))),
      protection =>
      PLAProtectionService.insertOrUpdateProtection(protection)
          .map { _ => Ok }
          .recover { case exception => Results.InternalServerError(exception.toString) }
    )
  }

  /**
    * Stub-only convenience operation to tear down test data
    * @return
    */
  def removeAllProtections(): Action[AnyContent] = Action.async { implicit request =>
    protectionsStore.removeProtectionsCollection()
    Future.successful(Ok)
  }

  /**
    * Stub-only convenience operation to tear down test data for a given NINO
    *
    * @param nino
    * @return
    */
  def removeProtections(nino: String): Action[AnyContent] = Action.async { implicit request =>
    protectionsStore.removeByNino(nino)
    Future.successful(Ok)
  }

  /**
    * Stub-only convenience operation to tear down test data for a specified protection
    *
    * @param nino
    * @param protectionId
    * @return
    */
  def removeProtection(nino: String, protectionId: Long): Action[AnyContent] = Action.async { implicit request =>
    PLAProtectionService.removeProtectionByNinoAndProtectionId(nino,protectionId)
    Future.successful(Ok)
  }

  /**
    * Stub-only convenience operation to tear down test data for a specified protection
    *
    * @return
    */
  def dropProtectionsCollection(): Action[AnyContent] = Action.async { implicit request =>
    protectionsStore.removeProtectionsCollection()
    Future.successful(Ok)
  }


  /**
   * Stub-only convenience operation to remove all exception triggers from the database
   * @return
   */
  def removeExceptionTriggers(): Action[AnyContent] = Action.async { implicit request =>
    exceptionTriggerRepository.removeAllExceptionTriggers()
    Future.successful(Ok)
  }

  /**
   * Stub-only convenience operation to add an exception trigger for a particular nino
   * @return
   */
  def insertExceptionTrigger(): Action[JsValue] = Action.async (BodyParsers.parse.json) { implicit request =>
    val exceptionTriggerJs = request.body.validate[ExceptionTrigger]
    exceptionTriggerJs.fold(
      errors => Future.successful(BadRequest(Json.toJson(Error(message="body failed validation with errors: " + errors)))),
      exceptionTrigger =>
        exceptionTriggerRepository.insert(exceptionTrigger)
          .map { _ => Ok }
          .recover { case exception => Results.InternalServerError(exception.toString) }
    )
  }



}
