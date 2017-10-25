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

package uk.gov.hmrc.pla.stub.services

import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.{BadRequest, NotFound, Ok}
import uk.gov.hmrc.pla.stub.model.Generator.pensionSchemeAdministratorCheckReferenceGen
import uk.gov.hmrc.pla.stub.model.{Generator, _}
import uk.gov.hmrc.smartstub._

import scala.concurrent.Future

object PLAProtectionService {

  lazy val protectionsStore = Generator.protectionsStore.empty
  def createLTAProtectionResponse(data: CreateLTAProtectionRequest): CreateLTAProtectionResponse = {
    val protection : Protection = Generator.genProtection(data.nino).sample.get
    val newLTAProtection = protection.copy(
      `type` = data.protection.`type`,
      status = data.protection.status,
      relevantAmount = data.protection.relevantAmount,
      preADayPensionInPayment = data.protection.preADayPensionInPayment,
      postADayBCE = data.protection.postADayBCE,
      uncrystallisedRights = data.protection.uncrystallisedRights,
      nonUKRights = data.protection.nonUKRights,
      pensionDebits = data.pensionDebits,
      certificateDate = data.protection.certificateDate,
      protectedAmount = data.protection.protectedAmount,
      protectionReference = data.protection.protectionReference
    )
    val protections = protectionsStore.get(data.nino)
    val pensionSchemeAdministratorCheckReference = pensionSchemeAdministratorCheckReferenceGen.sometimes.sample.get
    val ltaProtections : List[Protection] = protections match {
      case Some(protections) =>   newLTAProtection :: protections.protections
      case None =>  List(newLTAProtection)
    }
    protectionsStore(data.nino) = Protections(data.nino,pensionSchemeAdministratorCheckReference,ltaProtections)
    CreateLTAProtectionResponse(nino = data.nino,
      pensionSchemeAdministratorCheckReference = pensionSchemeAdministratorCheckReference,
      protection = newLTAProtection)
  }

  def updateLTAProtection(data: UpdateLTAProtectionRequest,nino:String,protectionId: Long) : Result = {
    if (!data.protection.withdrawnDate.isEmpty && data.protection.status != 3) {
      val errorMsg = "The status of a Protection must be set to WITHDRAWN when the Withdrawn Date is provided"
      Logger.error(errorMsg)
      BadRequest(Json.toJson(Error(errorMsg)))
    } else {
      val protections: Option[Protections] = PLAProtectionService.retrieveProtections(nino)
      val existingProtection: Option[Protection] = protections.get.protections.find(p => p.id == protectionId)
      existingProtection match {
        case Some(existingProtection) => Ok(Json.toJson(updateLTAProtectionResponse(existingProtection, data, protectionId)))
        case None => NotFound(Json.toJson(Error("Protection to update not found")))
      }
    }
  }

  def updateLTAProtectionResponse(protection: Protection,data: UpdateLTAProtectionRequest,protectionId: Long) : UpdateLTAProtectionResponse = {

    val updatedLTAProtection = protection.copy(
      `type` = data.protection.`type`,
      status = data.protection.status,
      relevantAmount = Some(data.protection.relevantAmount),
      preADayPensionInPayment = Some(data.protection.preADayPensionInPayment),
      postADayBCE = Some(data.protection.postADayBCE),
      uncrystallisedRights = Some(data.protection.uncrystallisedRights),
      nonUKRights = Some(data.protection.nonUKRights),
      pensionDebits = data.pensionDebits,
      withdrawnDate = data.protection.withdrawnDate
    )
    updateDormantProtectionStatusAsOpen(data.nino)
    val protections: Protections = PLAProtectionService.retrieveProtections(data.nino).get
    val ltaProtections : List[Protection] = updatedLTAProtection :: protections.protections.filter(_.id != protectionId)
    protectionsStore(data.nino) = protections.copy(protections=ltaProtections)
    UpdateLTAProtectionResponse(nino = data.nino,
      pensionSchemeAdministratorCheckReference = pensionSchemeAdministratorCheckReferenceGen.sometimes.sample.get,
      protection = updatedLTAProtection)
  }

  def updateDormantProtectionStatusAsOpen(nino:String) : Unit= {
    val protections: Protections = PLAProtectionService.retrieveProtections(nino).get
    protections.protections.find(_.status == 2) match {
      case Some(existingDormantProtection) => {
        val ltaProtections : List[Protection] = existingDormantProtection.copy(status = 1) :: protections.protections.filter(_.status != 2)
        protectionsStore(nino) = protections.copy(protections = ltaProtections)
      }
      case None => ()
    }
  }

  def retrieveProtections(nino: String): Option[Protections] = {
    protectionsStore.get(nino)
  }

}
