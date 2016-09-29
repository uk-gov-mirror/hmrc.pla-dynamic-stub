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

import uk.gov.hmrc.pla.stub.actions.ExceptionTriggersActions.WithExceptionTriggerCheckAction
import uk.gov.hmrc.pla.stub.notifications.{CertificateStatus, Notifications}
import uk.gov.hmrc.play.microservice.controller.BaseController

import play.api.mvc._
import play.api.libs.json._

import uk.gov.hmrc.pla.stub.repository.{MongoExceptionTriggerRepository, ExceptionTriggerRepository, ProtectionRepository, MongoProtectionRepository}
import uk.gov.hmrc.pla.stub.model._
import uk.gov.hmrc.pla.stub.rules._

import scala.Error
import scala.concurrent.Future
import scala.util.Random
import java.time.LocalDateTime

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * The controller for the Protect your Lifetime Allowance (PLA) service REST API dynamic stub
  */

object PLAStubController extends PLAStubController {
  override val protectionRepository = MongoProtectionRepository()
  override val exceptionTriggerRepository = MongoExceptionTriggerRepository()
}

trait PLAStubController extends BaseController {

	val protectionRepository: ProtectionRepository
  val exceptionTriggerRepository: ExceptionTriggerRepository

  /**
    * Get all current protections applicable to the specified Nino
    *
    * @param nino identifies the individual to whom the protections apply
    * @return List of latest versions of each protection
    **/

	def readProtections(nino: String) : Action[AnyContent] = WithExceptionTriggerCheckAction(nino).async { implicit request =>

    protectionRepository.findAllVersionsOfAllProtectionsByNino(nino).map { (protections: Map[Long,List[Protection]]) =>

      // get all current protections in the form expected in the result
      val resultProtections = protections.values
        .map { p => toResultProtection(p, None, setPreviousVersions = true) }
        .collect { case Some(protection) => protection }

      val psaCheckRef=genPSACheckRef(nino)

      val result = Protections(nino, Some(psaCheckRef), resultProtections.toList)
      Ok(Json.toJson(result))
    }
  }



  def readProtection(nino: String, protectionId: Long, version: Option[Int]) = WithExceptionTriggerCheckAction(nino).async { implicit request =>

    protectionRepository.findAllVersionsOfProtectionByNinoAndId(nino, protectionId) map { protectionHistory: List[Protection] =>
      toResultProtection(protectionHistory, version, setPreviousVersions = !version.isDefined)
        .map { result =>
          Ok(Json.toJson(result))
        }
        .getOrElse {
          // no matching protection
          val error = protectionHistory match {
            case Nil => Error("no protection found for specified protection id")
            case _ => Error("protection of specified id found, but no match for specified version")
          }
          NotFound(Json.toJson(error))
        }
    }
  }

  def createProtection(nino: String) = WithExceptionTriggerCheckAction(nino).async (BodyParsers.parse.json) { implicit request =>

    val protectionApplicationBodyJs = request.body.validate[CreateLTAProtectionRequest]
    val headers = request.headers.toSimpleMap
    val protectionApplicationJs = ControllerHelper.addExtraRequestHeaderChecks(headers, protectionApplicationBodyJs)


    protectionApplicationJs.fold(
      errors => Future.successful(BadRequest(Json.toJson(Error(message = "Request to create protection failed with validation errors: " + errors)))),
      createProtectionRequest =>
        createProtectionRequest.protection.requestedType
          .collect {
            // gather the relevant rules
            case Protection.Type.FP2016 => FP2016ApplicationRules
            case Protection.Type.IP2014 => IP2014ApplicationRules
            case Protection.Type.IP2016 => IP2016ApplicationRules
          }
          .map { appRules: ApplicationRules =>
            // apply the rules against any existing protections to determine the notification ID, and then process
            // the application according to that ID
            val existingProtectionsFut = protectionRepository.findLatestVersionsOfAllProtectionsByNino(nino)
            existingProtectionsFut flatMap { existingProtections: List[Protection] =>
              val notificationId = appRules.check(existingProtections)
              processApplication(nino, createProtectionRequest, notificationId, existingProtections)
            }
          }
          .getOrElse {
            val error = Error("invalid protection type specified")
            Future.successful(BadRequest(Json.toJson(error)))
          }
      )
    }

	def updateProtection(nino: String, protectionId: Long) = Action.async (BodyParsers.parse.json) { implicit request =>
    System.err.println("Amendment request body ==> " + request.body.toString)
    val protectionUpdateJs = request.body.validate[UpdateLTAProtectionRequest]
    protectionUpdateJs.fold(
      errors => Future.successful(BadRequest(Json.toJson(Error(message = "failed validation with errors: " + errors)))),
      updateProtectionRequest => {
        // first cross-check relevant amount against total of the breakdown fields, reject if discrepancy found
        val calculatedRelevantAmount =
          updateProtectionRequest.protection.nonUKRights +
          updateProtectionRequest.protection.postADayBCE +
          updateProtectionRequest.protection.preADayPensionInPayment +
            updateProtectionRequest.protection.uncrystallisedRights
        if (calculatedRelevantAmount != updateProtectionRequest.protection.relevantAmount) {
          Future.successful(BadRequest(Json.toJson(
            Error(message = "The specified Relevant Amount is not the sum of the specified breakdown amounts " +
              "(non UK Rights + Post A Day BCE + Pre A Day Pensions In Payment + Uncrystallised Rights)"))))
        }

        val amendmentTargetFutureOption = protectionRepository.findLatestVersionOfProtectionByNinoAndId(nino, protectionId)
        amendmentTargetFutureOption flatMap {
          case None =>
            Future.successful(NotFound(Json.toJson(Error(message = "protection to amend not found"))))
          case Some(amendmentTarget) if amendmentTarget.`type` != updateProtectionRequest.protection.`type` =>
            val error = Error("specified protection type does not match that of the protection to be amended")
            Future.successful(BadRequest(Json.toJson(error)))
          case Some(amendmentTarget) if amendmentTarget.version != updateProtectionRequest.protection.version =>
            val error = Error("specified protection version does not match that of the protection to be amended")
            Future.successful(BadRequest(Json.toJson(error)))
          case Some(amendmentTarget) =>
            protectionRepository.findLatestVersionsOfAllProtectionsByNino(nino) flatMap { existingProtections =>
              updateProtectionRequest.protection.requestedType
                .collect {
                  // gather the rules to be applied
                  case Protection.Type.IP2014 => IP2014AmendmentRules
                  case Protection.Type.IP2016 => IP2016AmendmentRules
                }
                .map { rules: AmendmentRules =>
                  // apply the rules against any existing protections to determine the notification ID, and then process
                  // the requested amendment according to that ID
                  val notificationId = rules.check(calculatedRelevantAmount, existingProtections)
                  processAmendment(nino, amendmentTarget, updateProtectionRequest, notificationId)
                }
                .getOrElse {
                  // no amendment rules matching specified protection type
                  val error = Error("invalid protection type specified")
                  Future.successful(BadRequest(Json.toJson(error)))
                }
            }
        }
      }
    )
  }

  /**
    * Facility for Pension Scheme Administartor (PSA) to lookup/verify protection details
    *
    * @param ref the protecion reference supplied to the PSA by the individual
    * @param psaref the PSA check reference supplied to the PSA by the individual
    * @return a simple result indicating whether valid certificate found, and if valid the type and relevant amount.
    */
  def psaLookup(ref: String, psaref: String) = Action.async (BodyParsers.parse.json) { implicit request =>
    // decode the Nino from the psa ref
    val c1 = psaref.substring(3, 4).toShort.toChar
    val c2 = psaref.substring(5, 6).toShort.toChar
    val nino = c1 + c2 + psaref.substring(7, 12)
    protectionRepository.findLatestVersionsOfAllProtectionsByNino(nino).map { (protections: List[Protection]) =>
      val result = protections.find(_.protectionReference.contains(ref))
      result match {
        case Some(protection) if protection.status == 1 =>
          Ok(Json.toJson(PSALookupResult(protection.`type`,validResult = true, protection.relevantAmount)))
        case _ => Ok(Json.toJson(PSALookupResult(0,validResult = false, None)))
      }
    }
  }

  // private methods

  /**
   * When passed an exception trigger, either returns the corresponding error response or throws he correct exception/timeout
   * @param trigger
   * @return
   */
  private def processExceptionTrigger(trigger: ExceptionTrigger): Future[Result] = {
    import ExceptionTrigger.ExceptionType
    trigger.extractedExceptionType match {
      case ExceptionType.BadRequest => Future.successful(BadRequest("Simulated bad request"))
      case ExceptionType.NotFound => Future.successful(NotFound("Simulated npot found"))
      case ExceptionType.InternalServerError => Future.successful(InternalServerError("Simulated 500 error"))
      case ExceptionType.BadGateway => Future.successful(BadGateway("Simulated 502 error"))
      case ExceptionType.ServiceUnavailable => Future.successful(ServiceUnavailable("Simulated 503 error"))
      case ExceptionType.UncaughtException => throw new Exception()
      case ExceptionType.Timeout => Thread.sleep(60000); Future.successful(Ok)
    }
  }

  /**
    * Process an application for a new protection for which we have determined the relevant notification ID.
    * A protection will be created and stored in the repository and then returned to the client. The protection
    * will include the notification ID and associated message.
    * A "protection" will be created and returned even for unsuccessful/rejected applications, which can occur
    * if existing protections for the individual do not allow the requested new protection. In such cases the
    * response status will be Conflict (409) and the stored/returned protection does not represent a valid certificate -
    * it just encapsulates the details of the non-successful application.
    *
    * @param nino Individuals NINO
    * @param applicationRequest Details of the application as parsed from the request body
    * @param notificationID Identifies the specific outcome of the application, which determines the detailed result
    * @return The result is a created protection (if successful) or a pseudo-protection object containing details of the
    *         error if unsuccessful.
    */
  private def processApplication(nino: String,
                                 applicationRequest: CreateLTAProtectionRequest,
                                 notificationID: Short,
                                 existingProtections: List[Protection]): Future[Result] = {

    val notificationEntry = Notifications.table(notificationID)

    // generate some validly formatted protection reference, if the notification ID indicates a scenario where one is
    // expected
    val protectionReference: Option[String] = notificationID match {
      case 3 | 4 | 8 => Some(("IP14" + Math.abs(Random.nextLong)).substring(0,9) + "A")
      case 12 => Some(("IP16" + Math.abs(Random.nextLong)).substring(0,9) + "B")
      case 22 | 23 =>  Some(("FP16" + Math.abs(Random.nextLong)).substring(0,9) + "C")
      case _ => None
    }

    val notificationMessage =
      injectMessageParameters(
        notificationEntry.message,
        applicationRequest.protection.`type`,
        applicationRequest.protection.relevantAmount,
        protectionReference,
        genPSACheckRef(nino))

    val successfulApplication = notificationEntry.status match {
      case CertificateStatus.Unsuccessful | CertificateStatus.UnknownStatus | CertificateStatus.Rejected => false
      case _ => true
    }

    val currDate = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
    val currTime = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME)

    val newProtection = Protection(
      nino = nino,
      version = 1,
      // NPS has documented API range constraint 0..4294967295 (i.e. unsigned int full range) for 'id' - code below
      // generates values uniformly distributed across this range (represented as Long since Scala doesn't have an unsigned
      // int type)
      id = Random.nextInt & 0x00000000ffffffffL,
      `type`=applicationRequest.protection.`type`,
      protectionReference=protectionReference,
      status = Notifications.extractedStatus(notificationEntry.status),
      notificationID = Some(notificationID),
      notificationMsg = Some(notificationMessage),
      certificateDate = if (successfulApplication) Some(currDate) else None,
      certificateTime = if (successfulApplication) Some(currTime) else None,
      relevantAmount = applicationRequest.protection.relevantAmount,
      protectedAmount = if(applicationRequest.protection.`type` == 1) Some(1250000.00) else applicationRequest.protection.relevantAmount,
      preADayPensionInPayment = applicationRequest.protection.preADayPensionInPayment,
      postADayBCE = applicationRequest.protection.postADayBCE,
      uncrystallisedRights = applicationRequest.protection.uncrystallisedRights,
      nonUKRights = applicationRequest.protection.nonUKRights,
      pensionDebits= applicationRequest.pensionDebits)

    // certain notifications require changing state of the currently open existing protection to dormant
    val doMaybeUpdateExistingProtection: Future[Any] = notificationID match {
      case 23 =>
        val currentlyOpen = existingProtections.find(_.status==Protection.extractedStatus(Protection.Status.Open))
        currentlyOpen map { openProtection =>
          // amend the protection, giving it a Dormant status & updating the certificate date
          val nowDormantProtection = openProtection.copy(
            status = Protection.extractedStatus(Protection.Status.Dormant),
            version = openProtection.version + 1,
            certificateDate = Some(currDate),
            certificateTime = Some(currTime))
          protectionRepository.insert(nowDormantProtection)
        } getOrElse Future.failed(new Exception("No open protection found, but notification ID indicates one should exist"))
      case 3 =>
        val currentlyOpen = existingProtections.find(_.status==Protection.extractedStatus(Protection.Status.Open))
        currentlyOpen map { openProtection =>
          // amend the protection, giving it a Withdrawn status & updating the certificate date
          val nowWithdrawnProtection = openProtection.copy(
            status = Protection.extractedStatus(Protection.Status.Withdrawn),
            version = openProtection.version + 1,
            certificateDate = Some(currDate),
            certificateTime = Some(currTime))
          protectionRepository.insert(nowWithdrawnProtection)
        } getOrElse Future.failed(new Exception("No open protection found, but notification ID indicates one should exist"))
      case 8 =>
        val currentlyOpen = existingProtections.find(_.status==Protection.extractedStatus(Protection.Status.Open))
        currentlyOpen map { openProtection =>
          // amend the protection, giving it a Dormant status & updating the certificate date
          val nowDormantProtection = openProtection.copy(
            status = Protection.extractedStatus(Protection.Status.Dormant),
            version = openProtection.version + 1,
            certificateDate = Some(currDate),
            certificateTime = Some(currTime))
          protectionRepository.insert(nowDormantProtection)
        } getOrElse Future.failed(new Exception("No open protection found, but notification ID indicates one should exist"))
      case _ => Future.successful()  // no update needed for existing protections
    }

    val doUpdateProtections = for {
      _ <- doMaybeUpdateExistingProtection
      done <- protectionRepository.insert(newProtection)
    } yield done

    val response = CreateLTAProtectionResponse(nino = nino, pensionSchemeAdministratorCheckReference = Some(genPSACheckRef(nino)), protection = newProtection.copy(notificationMsg = None))

    val responseBody = Json.toJson(response)

    val result = if (successfulApplication) {
      Ok(responseBody)
    } else {
      if (notificationEntry.status==CertificateStatus.UnknownStatus)
      {
        // should never happen
        InternalServerError(Json.toJson(responseBody))
      }
      else
      {
        // unsuccessful/rejected due to conflict with some existing protection
        Conflict(responseBody)
      }
    }

    doUpdateProtections
      .map { _ => result }
      .recover { case exception => Results.InternalServerError(exception.toString) }
  }

  /**
    * Process an amendment request for which we have determined the appropriate notification ID.
    * The new version of the protection will be created and stored in the repository.
    * This may also result in a change to another protection, if the amendment requires the current
    * certificate to be withdrawn and therefore another protection to become active.
    *
    * @param nino individuals NINO
    * @param current The current version of the protection to be amended
    * @param amendmentRequest Details of the requested amendment, as parsed from the request body.
    * @param notificationId The notification Id resulting from the business rule checks
    * @return Updated protection result
    */
  private def processAmendment(nino: String,
                               current: Protection,
                               amendmentRequest: UpdateLTAProtectionRequest,
                               notificationId: Short): Future[Result] = {

    val notificationEntry = Notifications.table(notificationId)

    val newProtectionOpt = notificationEntry.status match {
      case CertificateStatus.Withdrawn =>
        // the amended protection will be withdrawn
        val protectionReference = amendmentRequest.protection.requestedType collect {
          case Protection.Type.IP2014 => ("IP14" + Math.abs(Random.nextLong)).substring(0, 9) + "A"
          case Protection.Type.IP2016 => ("IP16" + Math.abs(Random.nextLong)).substring(0, 9) + "B"
        }
        val notificationMessage =
          injectMessageParameters(
            notificationEntry.message,
            current.`type`,
            Some(amendmentRequest.protection.relevantAmount),
            protectionReference,
            genPSACheckRef(nino))

        // TODO
        // this doesn't actually reflect the business rules - in some 'withdrawn' cases we don't create a new
        // protection as below. Instead a dormant one can become open, and we simply update the amendee status to
        // withdrawn
        val currDate = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val currTime = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME)

        Some(Protection(
          nino = nino,
          version = 1,
          id = Random.nextLong,
          `type` = amendmentRequest.protection.`type`, // should be unchanged
          protectionReference = protectionReference,
          status = Notifications.extractedStatus(notificationEntry.status),
          notificationID = Some(notificationId),
          notificationMsg = Some(notificationMessage),
          certificateDate = Some(currDate),
          certificateTime = Some(currTime),
          relevantAmount = Some(amendmentRequest.protection.relevantAmount),
          preADayPensionInPayment = Some(amendmentRequest.protection.preADayPensionInPayment),
          postADayBCE = Some(amendmentRequest.protection.postADayBCE),
          uncrystallisedRights = Some(amendmentRequest.protection.uncrystallisedRights),
          nonUKRights = Some(amendmentRequest.protection.nonUKRights),
          pensionDebits = amendmentRequest.pensionDebits)
        )

      case _ => None
    }

    val amendedProtection = notificationEntry.status match {

      case CertificateStatus.Withdrawn =>
        current.copy(version = current.version + 1, status = Protection.extractedStatus(Protection.Status.Withdrawn))

      case _ =>
        val protectionReference: Option[String] = amendmentRequest.protection.requestedType
              .collect {
                case Protection.Type.IP2014 => ("IP14" + Math.abs(Random.nextLong)).substring(0, 9) + "A"
                case Protection.Type.IP2016 => ("IP16" + Math.abs(Random.nextLong)).substring(0, 9) + "B"
              }
              .filter(_ => notificationEntry.status==CertificateStatus.Open) // only generate ref if certificate is Open

        val notificationMessage =
          injectMessageParameters(
            notificationEntry.message,
            current.`type`,
            Some(amendmentRequest.protection.relevantAmount),
            protectionReference,
            genPSACheckRef(nino))

        val currDate = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val currTime = LocalDateTime.now.format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME)

        Protection(
          nino = nino,
          version = current.version + 1,
          id = current.id,
          `type` = current.`type`,
          protectionReference = current.protectionReference,
          status = Notifications.extractedStatus(notificationEntry.status),
          certificateDate = Some(currDate),
          certificateTime = Some(currTime),
          notificationID= Some(notificationId),
          notificationMsg = Some(notificationMessage),
          relevantAmount = Some(amendmentRequest.protection.relevantAmount),
          preADayPensionInPayment = Some(amendmentRequest.protection.preADayPensionInPayment),
          postADayBCE = Some(amendmentRequest.protection.postADayBCE),
          uncrystallisedRights = Some(amendmentRequest.protection.uncrystallisedRights),
          nonUKRights = Some(amendmentRequest.protection.nonUKRights),
          pensionDebits = amendmentRequest.pensionDebits)
    }

    val responseProtection = (newProtectionOpt getOrElse amendedProtection).copy(notificationMsg = None)
    val okResponse = UpdateLTAProtectionResponse(nino, Some(genPSACheckRef(nino)), responseProtection)
    val okResponseBody = Json.toJson(okResponse)
    val result = Ok(okResponseBody)

    val doMaybeCreateNewProtectionFut: Future[Any] = newProtectionOpt map { newProtection =>
        protectionRepository.insert(newProtection)
    } getOrElse Future.successful(None)

    val doAmendProtectionFut = protectionRepository.insert(amendedProtection)

    val updateRepoFut = for {
      _ <- doMaybeCreateNewProtectionFut
      done <- doAmendProtectionFut
    } yield done

    updateRepoFut map { _ => result } recover { case x => InternalServerError(x.toString) }
  }

  // helper for generating a  test PSA Check Ref from a test Nino (n.b. only the stub does this)
  private def ninoChar2TwoDigits(nc: Char) = nc.toShort.toString.substring(0,1)
  private val psaCheckRefLastCharsList="ABCDEFGHJKLMNPRSTXYZ".toList
  private def randomLastPSAChar=Random.shuffle(psaCheckRefLastCharsList).head
  private def genPSACheckRef(nino: String) = {
    val d1d2 = ninoChar2TwoDigits(nino.charAt(0))
    val d3d4 = ninoChar2TwoDigits(nino.charAt(1))
    "PSA" + d1d2 + d3d4 + nino.substring(2,7) + nino.head
  }

  /**
    * Convert a stored protection history to the result protection object returned to the client
    *
    * @param protectionVersions - must have at least one entry. The first entry is always assumed to be the
    *                          latest version.
    * @param version           if set then the result is the specified version of the protection,
    *                          otherwise just returns the latest version
    * @param setPreviousVersions if true then fill in the previousVersions field of the result, if and only if
    *                            it is latest version of the protection that is returned as the overall result.
    **/
  private def toResultProtection(protectionVersions: List[Protection],
                                 version: Option[Int],
                                 setPreviousVersions: Boolean)(implicit request: Request[AnyContent]): Option[Protection] = {
    val protectionOpt =
      version.fold(protectionVersions.headOption)(v => protectionVersions.find(_.version == v))
    protectionOpt map { protection: Protection =>
      // generate previousVersions (if applicable) as well as self field into the result protection
      val previousVersionList = Some(protectionVersions.tail map { p =>
        routes.PLAStubController.readProtection(p.nino, p.id, Some(p.version)).absoluteURL()
      }) filter(_ => setPreviousVersions)
      // val self = Some(routes.PLAStubController.readProtection(protection.nino, protection.id, None).absoluteURL())
      protection.copy(previousVersions = previousVersionList, notificationMsg = None)
    }
  }

  /**
    * Inject any relevant parameter values from Protection into the notification message
    *
    * @param messageTemplate the static notification message template, which may contain parameter identifiers of format
    *                        '#{param_name}' into which the associated parameter value is injected if available.
    * @param protectionType parameter value: the type of the protection
    * @param relevantAmount optional parameter value: the relevant amount, if available
    * @param protectionReference optional parameter value: the protecion reference, if available.
    * @param psaCheckRef: parameter value: the generated PSA check reference
    * @return
    */
  private def injectMessageParameters(messageTemplate: String,
                                      protectionType: Int,
                                      relevantAmount: Option[Double],
                                      protectionReference: Option[String],
                                      psaCheckRef: String): String = {
    val injectAmount = relevantAmount map { amount =>
      protectionType match {
        case 2 => Math.min(amount, 1500000.00)
        case 3 => Math.min(amount, 1250000.00)
        case _ => amount
      }
    } getOrElse 0.0
    val injectProtectionRef = protectionReference.getOrElse("<NONE>")
    messageTemplate
      .replace("#{amount}",injectAmount.toString)
      .replace("#{reference}",injectProtectionRef)
      .replace("#{psa_reference}",psaCheckRef)
  }
}

object ControllerHelper {
  /*
   * Checks that the standard extra headers required for NPS requests are present in a request
   * @param headers a simple map of all request headers
   * @param the result of validating the request body
   * @rreturn the overall validation result, of non-success then will include both body and  header validation errors
   */
  def  addExtraRequestHeaderChecks[T](headers: Map[String,String], bodyValidationResultJs: JsResult[T]): JsResult[T] = {
    val environment = headers.get("Environment")
    val token = headers.get("Authorization")
    val notSet = "<NOT SET>"
    play.Logger.info("Request headers: environment =" + environment.getOrElse(notSet) + ", authorisation=" + token.getOrElse(notSet))

    //  Ensure any header validation errors are accumulated with any body validation errors into a single JsError
    //  (the below code is not so nice, could be a good use case for scalaz validation)
    val noAuthHeaderErr = JsError("required header 'Authorisation' not set in NPS request")
    val noEnvHeaderErr = JsError("required header 'Environment' not set in NPS request")
    // 1. accumlate any header errors
    def headerNotPresentErrors: Option[JsError] = (environment, token) match {
      case (Some(_), Some(_)) => None
      case (Some(_), None) =>   Some(noAuthHeaderErr)
      case (None, Some(_)) => Some(noEnvHeaderErr)
      case (None, None) => Some(noAuthHeaderErr ++ noEnvHeaderErr)
    }
    // 2. accumulate any header + any body errors
    (bodyValidationResultJs, headerNotPresentErrors) match {
      case (e1: JsError, e2: Some[JsError]) => e1 ++ e2.get
      case (e1: JsError, _) => e1
      case (_, e2: Some[JsError]) => e2.get
      case _ => bodyValidationResultJs // success case
    }
  }
}

