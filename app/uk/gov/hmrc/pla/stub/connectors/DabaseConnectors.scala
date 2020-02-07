/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.pla.stub.connectors

import reactivemongo.api.{DefaultDB, FailoverStrategy}
import uk.gov.hmrc.mongo.SimpleMongoConnection

import scala.concurrent.duration.FiniteDuration

class NonConnectingMongoConnection extends SimpleMongoConnection {
  override val mongoConnectionUri: String = "NonConnectingMongoConnection"
  override val failoverStrategy: Option[FailoverStrategy] = None
  override val dbTimeout: Option[FiniteDuration] = None

  override implicit def db: () => DefaultDB = throw new RuntimeException("ONLY STUB MONGO DB")
}