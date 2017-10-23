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
import uk.gov.hmrc.smartstub.Enumerable.instances.ninoEnumNoSpaces
import uk.gov.hmrc.pla.stub.model.{Generator, _}
import uk.gov.hmrc.smartstub._

import scala.concurrent.Future

object PLAProtectionService {

  lazy val protectionsStore = Generator.protectionsStore.empty

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

  def insertOrUpdateProtection(protection : Protection) : Future[Result] = {
    val protections = protectionsStore.get(protection.nino)
    val pensionSchemeAdministratorCheckReference = pensionSchemeAdministratorCheckReferenceGen.sample
    val ltaProtections : List[Protection] = protections match {
      case Some(protections) =>   protection :: protections.protections.filter(_.id != protection.id)
      case None =>  List(protection)
    }
    protectionsStore(protection.nino) = Protections(protection.nino,pensionSchemeAdministratorCheckReference,ltaProtections)
    Future.successful(Ok)
  }

  def removeProtectionByNinoAndProtectionId(nino: String,protectionId : Long) : Future[Result] = {
    val protections: Protections = PLAProtectionService.retrieveProtections(nino).get
    protections.protections.find(_.id == protectionId) match {
      case Some(_) => {
        val ltaProtections : List[Protection] = protections.protections.filter(_.id != protectionId)
        protectionsStore(nino) = protections.copy(protections = ltaProtections)
        Future.successful(Ok)
      }
      case None => Future.successful(NotFound)
    }
  }

  def findAllProtectionsByNino(nino: String): Option[List[Protection]] = {
    PLAProtectionService.retrieveProtections(nino) match {
      case Some(protections) => Some(protections.protections)
      case _ => None}
  }

  def findProtectionByNinoAndId(nino:String, protectionId: Long): Option[Protection] = {
    val protections: Option[Protections] = PLAProtectionService.retrieveProtections(nino)
    protections.get.protections.find(p => p.id == protectionId)
  }

}
