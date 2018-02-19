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

package uk.gov.hmrc.pla.stub.services

import play.api.mvc.Result
import play.api.mvc.Results.{NotFound, Ok}
import reactivemongo.api.commands.WriteResult
import uk.gov.hmrc.pla.stub.model.Generator.pensionSchemeAdministratorCheckReferenceGen
import uk.gov.hmrc.pla.stub.model._
import uk.gov.hmrc.pla.stub.repository.MongoProtectionRepository

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object PLAProtectionService {

  lazy val protectionsStore = MongoProtectionRepository.apply()

  def saveProtections(protections: Protections): Future[Future[WriteResult]] = {
    def save(deleted: Unit, data: Protections): Future[WriteResult] = protectionsStore.insertProtection(data)
    protectionsStore.removeByNino(protections.nino).map(remove => save(remove, protections))
  }

  def updateDormantProtectionStatusAsOpen(nino: String): Future[Unit] = {
    val futProtections: Future[Protections] = PLAProtectionService.retrieveProtections(nino).map(_.get)
    futProtections.map { protections =>
      protections.protections.find(_.status == 2) match {
        case Some(existingDormantProtection) => {
          val ltaProtections: List[Protection] = existingDormantProtection.copy(status = 1) :: protections.protections.filter(_.status != 2)
          saveProtections(protections.copy(protections = ltaProtections))
        }
        case None => ()
      }
    }
  }

  def retrieveProtections(nino: String): Future[Option[Protections]] = {
    protectionsStore.findProtectionsByNino(nino)
  }

  def insertOrUpdateProtection(protection: Protection): Future[Result] = {
    val protections = protectionsStore.findProtectionsByNino(protection.nino)
    val pensionSchemeAdministratorCheckReference = pensionSchemeAdministratorCheckReferenceGen.sample
    def ltaProtections(optProtections: Option[Protections]): Future[List[Protection]] = Future {
      optProtections match {
        case Some(value) => protection :: value.protections.filter(_.id != protection.id)
        case None => List(protection)
      }
    }
    def listToProtections(list: List[Protection]): Future[Protections] = Future(Protections(protection.nino, pensionSchemeAdministratorCheckReference, list))
    for {
      optProtections <- protections
      updatedProtections <- ltaProtections(optProtections)
      result <- listToProtections(updatedProtections)
      save <- saveProtections(result)
    } yield Ok
  }

  def removeProtectionByNinoAndProtectionId(nino: String, protectionId: Long): Future[Result] = {
    val futureProtections: Future[Protections] = PLAProtectionService.retrieveProtections(nino).map(_.get)
    futureProtections.map { protections => protections.protections.find(_.id == protectionId) match {
      case Some(_) => {
        val ltaProtections: List[Protection] = protections.protections.filter(_.id != protectionId)
        saveProtections(protections.copy(protections = ltaProtections))
        Ok
      }
      case None => NotFound
    }
    }
  }

  def findAllProtectionsByNino(nino: String): Future[Option[List[Protection]]] = {
    PLAProtectionService.retrieveProtections(nino).map {
      case Some(protections) => Some(protections.protections)
      case _ => None
    }
  }

  def findProtectionByNinoAndId(nino: String, protectionId: Long): Future[Option[Protection]] = {
    val protections: Future[Option[Protections]] = PLAProtectionService.retrieveProtections(nino)
    protections.map {
      _.get.protections.find(p => p.id == protectionId)
    }
  }
}
