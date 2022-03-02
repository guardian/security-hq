package aws.ec2

import com.amazonaws.services.elasticfilesystem.model.DescribeMountTargetSecurityGroupsResult
import model.{EfsVolume, SGInUse, UnknownUsage}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.{Checkers, ScalaCheckPropertyChecks}
import utils.attempt.AttemptValues

class EFSTest extends AnyFreeSpec with Matchers with Checkers with ScalaCheckPropertyChecks with AttemptValues {
  "getsEfsSecurityGroups" - {
    "getEfsSecurityGroupResult" in {
      val describeSecGrpResultOne = new DescribeMountTargetSecurityGroupsResult().withSecurityGroups("sg-12345")
      val describeSecGrpResultTwo = new DescribeMountTargetSecurityGroupsResult().withSecurityGroups("sg-23456")
      val efsSecGrps = List(("EFS 12345", describeSecGrpResultOne), ("EFS 23456", describeSecGrpResultTwo))
      EFS.secGrpToKey(efsSecGrps) shouldEqual List(("sg-12345", model.EfsVolume("EFS 12345")), ("sg-23456", model.EfsVolume("EFS 23456")))
    }

    "getEfsFlaggedSecurityGroups" in {
      val flaggedSecGrps = List("sg-12345")
      val allEfsSecGrps = List(("sg-12345", model.EfsVolume("EFS 12345")), ("sg-23456", model.EfsVolume("EFS 12345")))
      EFS.getFlaggedSecGrps(flaggedSecGrps, allEfsSecGrps) shouldEqual List(("sg-12345", model.EfsVolume("EFS 12345")))
    }
    "removeUnwantedUnknownUsageEfsValues" in {
      val secGrpToResources: Map[String, Set[SGInUse]] = Map(
        "sg-123456789" -> Set(
        UnknownUsage("EFS mount target for fs-1234 (fsmt-1234)","eni-1234"),
        UnknownUsage("EFS mount target for fs-1234 (fsmt-2345)","eni-2345"),
        UnknownUsage("EFS mount target for fs-1234 (fsmt-3456)","eni-3456"),
        EfsVolume("fs-1234"),
        UnknownUsage("test","test")))
      val result: Map[String, Set[SGInUse]] = Map("sg-123456789" -> Set(EfsVolume("fs-1234"), UnknownUsage("test","test")))
      EFS.filterOutEfsUnknownUsages(secGrpToResources) shouldEqual result
    }
  }
}
