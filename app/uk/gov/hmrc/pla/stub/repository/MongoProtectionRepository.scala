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

import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.DB
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}
import uk.gov.hmrc.pla.stub.model.Protections

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

/**
  * Mongo repository for use by PLA dynamic stub to store/retrieve protections for an individual
  */

object MongoProtectionRepository extends MongoDbConnection {
  private lazy val repository = new MongoProtectionRepository

  def apply(): MongoProtectionRepository = repository
}

trait ProtectionRepository extends Repository[Protections, BSONObjectID] {

  def findAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]]

  def findProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[Option[Protections]]

  def insertProtection(protections: Protections): Future[WriteResult]

  /**
    * Housekeeping op to remove all protections associated with a nino
    */
  def removeByNino(nino: String)(implicit ec: ExecutionContext): Future[Unit]


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
  extends ReactiveRepository[Protections, BSONObjectID]("protections", mongo, Protections.protectionsFormat)
    with ProtectionRepository {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("nino" -> IndexType.Ascending, "id" -> IndexType.Ascending, "version" -> IndexType.Ascending),
      name = Some("ninoIdAndVersionIdx"),
      unique = true, // this should ensure concurrent amendments can't create two objects with same version
      sparse = true)
  )

  override def findAllProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[List[Protections]] = {
    find("nino" -> nino)
  }

  override def findProtectionsByNino(nino: String)(implicit ec: ExecutionContext): Future[Option[Protections]] = {
    findAllProtectionsByNino(nino).map {
      _.headOption
    }
  }

  override def removeByNino(nino: String)(implicit ec: ExecutionContext): Future[Unit] =
    remove("nino" -> nino).map { _ => }


  override def removeAllProtections()(implicit ec: ExecutionContext): Future[Unit] =
    removeAll(WriteConcern.Acknowledged).map { _ => }

  override def removeProtectionsCollection()(implicit ec: ExecutionContext): Future[Boolean] =
    drop(ec)

  override def insertProtection(protections: Protections): Future[WriteResult] =
    insert(protections)
}
