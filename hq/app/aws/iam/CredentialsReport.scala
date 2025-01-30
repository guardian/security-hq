package aws.iam

import java.io.StringReader
import model.{AwsStack, CredentialReportDisplay, IAMCredential, IAMCredentialsReport}
import com.github.tototoshi.csv._
import org.joda.time.{DateTime, Hours, Seconds}
import logic.DateUtils
import net.logstash.logback.marker.Markers.appendEntries
import play.api.{Logging, MarkerContext}
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.jdk.CollectionConverters._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.iam.model.{GenerateCredentialReportResponse, GetCredentialReportResponse, ReportStateType}

object CredentialsReport extends Logging {

  def isComplete(report: GenerateCredentialReportResponse): Boolean =
    report.state == ReportStateType.COMPLETE

  def credentialsReportReadyForRefresh(currentReport: Either[FailedAttempt, CredentialReportDisplay], currentTime: DateTime): Boolean = {
    currentReport match {
      case Left(_) => true
      case Right(credentialReportDisplay) => {
        val timeSinceLastReport = Seconds.secondsBetween(credentialReportDisplay.reportDate, currentTime)
        timeSinceLastReport.isGreaterThan(Hours.hours(4).toStandardSeconds)
      }
    }
  }

  private[iam] def enrichReportWithStackDetails(report: IAMCredentialsReport, stacks: List[AwsStack]): IAMCredentialsReport = {
    val updatedEntries = report.entries.map { cred =>
      stacks
        .find(stack => cred.user.startsWith(stack.name) && cred.user.takeRight(12).matches("^[A-Z0-9]{12}$"))
        .fold(cred)(s => cred.copy(stack = Some(s)))
    }
    report.copy(entries = updatedEntries)
  }

  private[iam] def tryParsingReport(content: String) = {
    Try {
      parseCredentialsReport(content)
    } match {
      case Success(x)  if x.nonEmpty => Attempt.Right(x)
      case Success(_) =>
        Attempt.Left(utils.attempt.Failure(s"CREDENTIALS_PARSE_ERROR", "Credentials report is empty", 500))
      case Failure(th) =>
        Attempt.Left(utils.attempt.Failure(s"CREDENTIALS_PARSE_ERROR: ${th.getMessage}", "Cannot parse AWS credentials audit report", 500))
    }
  }

  def extractReport(response: GetCredentialReportResponse)(implicit  ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val report = response.content.asUtf8String()
    tryParsingReport(report).map(IAMCredentialsReport(new DateTime(response.generatedTime), _))
  }


  def parseBoolean(cell: String): Option[Boolean] = {
    if (cell == "true") Some(true)
    else if (cell == "false") Some(false)
    else None
  }

  def parseDateTimeOpt(cell: String): Option[DateTime] = {
    cell match {
      case "no_information" | "N/A" | "not_supported" => None
      case _ => Some(DateUtils.isoDateTimeParser.parseDateTime(cell))
    }
  }

  def parseRegionOpt(cell: String): Option[Region] = {
    if (cell == "N/A") None
    else Some(Region.of(cell))
  }

  def parseStrOpt(cell: String): Option[String] = {
    if (cell == "N/A") None
    else Some(cell)
  }

  def parseCredentialsReport(contents: String): List[IAMCredential] = {
      val iamCredentialsReport = CSVReader.open(new StringReader(contents)).allWithHeaders().map { row =>
        IAMCredential(
          user = row.getOrElse("user", "no username available"),
          arn = row.getOrElse("arn", "no ARN available"),
          creationTime = row.get("user_creation_time").flatMap(parseDateTimeOpt).get,
          stack = None,
          passwordEnabled = row.get("password_enabled").flatMap(parseBoolean),
          passwordLastUsed = row.get("password_last_used").flatMap(parseDateTimeOpt),
          passwordLastChanged = row.get("password_last_changed").flatMap(parseDateTimeOpt),
          passwordNextRotation = row.get("password_next_rotation").flatMap(parseDateTimeOpt),
          mfaActive = row.get("mfa_active").flatMap(parseBoolean).get,
          accessKey1Active = row.get("access_key_1_active").flatMap(parseBoolean).get,
          accessKey1LastRotated = row.get("access_key_1_last_rotated").flatMap(parseDateTimeOpt),
          accessKey1LastUsedDate = row.get("access_key_1_last_used_date").flatMap(parseDateTimeOpt),
          accessKey1LastUsedRegion = row.get("access_key_1_last_used_region").flatMap(parseRegionOpt),
          accessKey1LastUsedService = row.get("access_key_1_last_used_service").flatMap(parseStrOpt),
          accessKey2Active = row.get("access_key_2_active").flatMap(parseBoolean).get,
          accessKey2LastRotated = row.get("access_key_2_last_rotated").flatMap(parseDateTimeOpt),
          accessKey2LastUsedDate = row.get("access_key_2_last_used_date").flatMap(parseDateTimeOpt),
          accessKey2LastUsedRegion = row.get("access_key_2_last_used_region").flatMap(parseRegionOpt),
          accessKey2LastUsedService = row.get("access_key_2_last_used_service").flatMap(parseStrOpt),
          cert1Active = row.get("cert_1_active").flatMap(parseBoolean).get,
          cert1LastRotated = row.get("cert_1_last_rotated").flatMap(parseDateTimeOpt),
          cert2Active = row.get("cert_2_active").flatMap(parseBoolean).get,
          cert2LastRotated = row.get("cert_2_last_rotated").flatMap(parseDateTimeOpt)
        )
      }

    iamCredentialsReport.filter(x => x.passwordEnabled.contains(true)).foreach(iamCred => {
      val mandatoryMarkers = Map(
        "User" -> iamCred.user,
        "PasswordEnabled" -> iamCred.passwordEnabled.getOrElse(false),
        "Arn" -> iamCred.arn
      )

      val markers = MarkerContext(appendEntries(mandatoryMarkers.asJava))
      logger.info(s"${iamCred.user} user has non-Janus access to AWS: $iamCred")(markers)
    })

    iamCredentialsReport
  }
}
