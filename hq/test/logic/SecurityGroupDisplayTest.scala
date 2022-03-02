package logic

import model._
import SecurityGroupDisplay._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers


class SecurityGroupDisplayTest extends AnyFreeSpec with Matchers {
  "resourceIcons" - {
    "returns zeros when there are no resources in use" in {
      val usages = List[SGInUse]()
      resourceIcons(usages).instances shouldEqual 0
      resourceIcons(usages).elbs shouldEqual 0
      resourceIcons(usages).unknown shouldEqual 0
    }

    "returns individual counts for the different resource types" in {
      val usages = List[SGInUse](Ec2Instance("instance"), ELB("elb-1"), ELB("elb-2"), UnknownUsage("unknown", ""), EfsVolume("fs-12345"))
      resourceIcons(usages).instances shouldEqual 1
      resourceIcons(usages).elbs shouldEqual 2
      resourceIcons(usages).unknown shouldEqual 1
      resourceIcons(usages).efss shouldEqual 1
    }
  }

  "splitReportsBySuppressed" - {
    val flaggedA = SGOpenPortsDetail("Ok", "eu-west-1", "name-1", "sg-1", "vpc-1", "tcp", "1099", "Yellow", isSuppressed = false, None, None)
    val flaggedB = SGOpenPortsDetail("Ok", "eu-west-1", "name-2", "sg-2", "vpc-2", "tcp", "1099", "Yellow", isSuppressed = false, None, None)
    val suppressedA = SGOpenPortsDetail("Ok", "eu-west-1", "name-3", "sg-3", "vpc-3", "tcp", "1099", "Yellow", isSuppressed = true, None, None)
    val suppressedB = SGOpenPortsDetail("Ok", "eu-west-1", "name-4", "sg-4", "vpc-4", "tcp", "1099", "Yellow", isSuppressed = true, None, None)

    "returns two lists when there are only flagged Security Groups, and no suppressed" in {
      val sgReport = List((flaggedA, Set[SGInUse]()), (flaggedB, Set[SGInUse]()))
      splitReportsBySuppressed(sgReport).suppressed.length shouldEqual 0
      splitReportsBySuppressed(sgReport).flagged.length shouldEqual 2
    }

    "returns two lists when there are both flagged and suppressed Security Groups" in {
      val sgReport = List((flaggedA, Set[SGInUse]()), (suppressedA, Set[SGInUse]()), (flaggedB, Set[SGInUse]()), (flaggedA, Set[SGInUse]()))
      splitReportsBySuppressed(sgReport).suppressed.length shouldEqual 1
      splitReportsBySuppressed(sgReport).flagged.length shouldEqual 3
    }

    "will place each Security Group into the correct list" in {
      val sgReport = List((suppressedA, Set[SGInUse]()), (flaggedA, Set[SGInUse]()), (flaggedB, Set[SGInUse]()), (suppressedB, Set[SGInUse]()))
      splitReportsBySuppressed(sgReport).suppressed shouldEqual List((suppressedA, Set()), (suppressedB, Set()))
      splitReportsBySuppressed(sgReport).flagged shouldEqual List((flaggedA, Set()), (flaggedB, Set()))
    }
  }
}
