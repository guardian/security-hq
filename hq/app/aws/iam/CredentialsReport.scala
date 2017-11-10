package aws.iam

import java.io.StringReader

import model.{IAMCredential, IAMCredentialsReport}
import com.github.tototoshi.csv._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.identitymanagement.model.{GenerateCredentialReportResult, GetCredentialReportResult}


object CredentialsReport {

  def isComplete(report: GenerateCredentialReportResult) = report.getState == "COMPLETE"

  def extractReport(result: GetCredentialReportResult): IAMCredentialsReport = {
    val content = result.getContent
    val dest = new Array[Byte](content.capacity())
    content.get(dest, 0, dest.length)
      IAMCredentialsReport(
        new DateTime(result.getGeneratedTime),
        parseCredentialsReport(new String(dest))
      )
  }

  // parsing the credentials report CSV file
  val isoDateTimeParser = ISODateTimeFormat.dateTimeParser().withZoneUTC()

  def parseBoolean(cell: String): Option[Boolean] = {
    if (cell == "true") Some(true)
    else if (cell == "false") Some(false)
    else None
  }

  def parseDateTimeOpt(cell: String): Option[DateTime] = {
    if (cell == "N/A" || cell == "not_supported") None
    else Some(isoDateTimeParser.parseDateTime(cell))
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
    val report = CSVReader.open(new StringReader(contents)).allWithHeaders().map { row =>
      IAMCredential(
        user = row.getOrElse("user", "no username available"),
        arn = row.getOrElse("arn", "no ARN available"),
        creationTime = row.get("user_creation_time").flatMap(parseDateTimeOpt).get,
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

    report
  }
}
