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

package uk.gov.hmrc.pla.stub.repository

import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.DB
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.pla.stub.model.{Protection, Protections}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Mongo repository for use by PLA dynamic stub to store/retrieve protections for an individual
  */

object MongoProtectionRepository extends MongoDbConnection {
  private lazy val repository = new MongoProtectionRepository

  def apply() : MongoProtectionRepository = repository
}

trait ProtectionRepository extends Repository[Protections, BSONObjectID] {

  def findAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]]

  def findProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[Option[Protections]]
  /*
   * Find all protections for the given nino, returning for each protection a list of all versions of the protection
   * @return a map of protectionId to list of versions of the associated protection - each version list is ordered by
   * i.e. the latest version of the protection is the first element in the list
   */
//  def findAllVersionsOfAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[Map[Long, List[Protections]]]
//  /*
//   * Get latest version of each protection assocated with the specified nino
//   * @return a list of the protections for the nino, each entry being the latest version of that protection
//   */
//  def findLatestVersionsOfAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]]
//  /*
//   * Get all versions of the specified protection.
//   * @return list of versions of the specified protection, ordered by version with latest version
//   * being the first entry in the list
//   * The list will be empty if no versions of the specified protection are found
//   */
//  def findAllVersionsOfProtectionByNinoAndId(nino:String, protectionId: Long)(implicit ec: ExecutionContext): Future[List[Protections]]
//  /*
//   * @return latest version of specified protection, or None if not found
//   */
//  def findLatestVersionOfProtectionByNinoAndId(nino:String, protectionId: Long)(implicit ec: ExecutionContext): Future[Option[Protections]]

  def insertProtection(protections: Protections): Future[WriteResult]

  /**
    * Housekeeping op to remove all protections associated with a nino
    */
  def removeByNino(nino: String)(implicit ec: ExecutionContext): Future[Unit]

  /**
    * Housekeeping op to remove a specific protection (all versions)
    */
  def removeByNinoAndProtectionID(nino: String, protectionId: Long)(implicit ec: ExecutionContext): Future[Unit]

  /**
    * Housekeeping op to remove all protections
    */
  def removeAllProtections()(implicit ec: ExecutionContext): Future[Unit]

  /**
    * Housekeeping op to remove protections collection
    */
  def removeProtectionsCollection()(implicit ec: ExecutionContext): Future[Boolean]
}

class
MongoProtectionRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Protections, BSONObjectID]("protections", mongo,Protections.protectionsFormat)
  with ProtectionRepository
{

  override def indexes = Seq(
    Index(Seq("nino" -> IndexType.Ascending,"id" -> IndexType.Ascending,"version" -> IndexType.Ascending),
          name = Some("ninoIdAndVersionIdx"),
          unique = true, // this should ensure concurrent amendments can't create two objects with same version
          sparse = true)
  )

  override def findAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]] = {
    find("nino" -> nino)
  }

  override def findProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[Option[Protections]] = {
    findAllProtectionsByNino(nino).map {_.headOption}
  }

//
//  override def findAllVersionsOfAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext):
//      Future[Map[Long, List[Protections]]] = {
//    val allProtectionsFut = find("nino" -> nino)
//    allProtectionsFut map { allProtections =>
//      val grouped = allProtections.groupBy[Long](_.id)
//      grouped mapValues { protections => protections.sortBy(_.version).reverse }
//    }
//  }

//  override def findLatestVersionsOfAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]] = {
//    val allVersionsFut = findAllVersionsOfAllProtectionsByNino(nino)
//    allVersionsFut map { allVersions =>
//      allVersions.values.toList.map { protectionVersions => protectionVersions.head }
//    }
//  }

//  override def findAllVersionsOfProtectionByNinoAndId(nino: String, protectionId: Long)(implicit ec: ExecutionContext):
//      Future[List[Protections]] = {
//    val protectionVersionsFut: Future[List[Protections]] = find("nino" -> nino, "id" -> protectionId)
//    protectionVersionsFut map { protectionVersions =>
//      protectionVersions sortBy { _.version } reverse
//    }
//  }

//  override def findLatestVersionOfProtectionByNinoAndId(nino:String, protectionId: Long)(implicit ec: ExecutionContext): Future[Option[Protections]] =
//    findAllVersionsOfProtectionByNinoAndId(nino,protectionId).map { _.headOption }

  override def removeByNino(nino: String)(implicit ec: ExecutionContext): Future[Unit] =
    remove("nino" -> nino).map {_ => }

  override def removeByNinoAndProtectionID(nino: String, protectionId: Long)(implicit ec: ExecutionContext): Future[Unit] =
    remove("nino" -> nino, "id" -> protectionId).map {_ => }

  override def removeAllProtections()(implicit ec: ExecutionContext): Future[Unit] =
    removeAll(WriteConcern.Acknowledged).map {_ => }

  override def removeProtectionsCollection()(implicit ec: ExecutionContext): Future[Boolean] =
    drop(ec)

  override def insertProtection(protections: Protections): Future[WriteResult] =
    insert(protections)
}
