package aws.iam

import java.io.StringReader

import model.{IAMCredential, IAMCredentialsReport}
import com.github.tototoshi.csv._
import org.joda.time.DateTime
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportResult, GetCredentialReportResult}
import logic.DateUtils
import utils.attempt.Attempt

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._


object CredentialsReport {

  def isComplete(report: GenerateCredentialReportResult): Boolean = report.getState == "COMPLETE"

  private[iam] def enrichReportDetails(report: IAMCredentialsReport, stacks: List[Stack]): IAMCredentialsReport = {
    report.copy(entries = report.entries.map { cred =>
      val enrichedCred = for {
        output <- stacks.find(_.getOutputs.asScala.toList.exists(_.getOutputValue.contains(cred.arn)))
      } yield cred.copy(stackId = Some(output.getStackId), stackName = Some(output.getStackName))
      enrichedCred.getOrElse(cred)
    })
  }

  private[iam] def tryParsingReport(content: String) = {
    Try {
      parseCredentialsReport(content)
    } match {
      case Success(x)  if x.nonEmpty => Attempt.Right(x)
      case Success(x)  if x.isEmpty  =>
        Attempt.Left(utils.attempt.Failure(s"CREDENTIALS_PARSE_ERROR", "Credentials report is empty", 500))
      case Failure(th) =>
        Attempt.Left(utils.attempt.Failure(s"CREDENTIALS_PARSE_ERROR: ${th.getMessage}", "Cannot parse AWS credentials audit report", 500))
    }
  }

  def extractReport(result: GetCredentialReportResult)(implicit  ec: ExecutionContext): Attempt[IAMCredentialsReport] = {
    val content = result.getContent
    val dest = new Array[Byte](content.capacity())
    content.get(dest, 0, dest.length)
    tryParsingReport(new String(dest)) map (IAMCredentialsReport(new DateTime(result.getGeneratedTime), _))
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
    else Some(Region.getRegion(Regions.fromName(cell)))
  }

  def parseStrOpt(cell: String): Option[String] = {
    if (cell == "N/A") None
    else Some(cell)
  }

  def parseCredentialsReport(contents: String): List[IAMCredential] = {
      CSVReader.open(new StringReader(contents)).allWithHeaders().map { row =>
        IAMCredential(
          user = row.getOrElse("user", "no username available"),
          arn = row.getOrElse("arn", "no ARN available"),
          creationTime = row.get("user_creation_time").flatMap(parseDateTimeOpt).get,
          stackId = None,
          stackName = None,
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
  }
}
