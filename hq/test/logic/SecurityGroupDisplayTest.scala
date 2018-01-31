package logic

import model._
import org.scalatest.{FreeSpec, Matchers}
import SecurityGroupDisplay._


class SecurityGroupDisplayTest extends FreeSpec with Matchers {
  "resourceIcons" - {
    "returns zeros when there are no resources in use" in {
      val usages = List[SGInUse]()
      resourceIcons(usages).instances shouldEqual 0
      resourceIcons(usages).elbs shouldEqual 0
      resourceIcons(usages).unknown shouldEqual 0
    }

    "returns individual counts for the different resource types" in {
      val usages = List[SGInUse](Ec2Instance("instance"), ELB("elb-1"), ELB("elb-2"), UnknownUsage("unknown", ""))
      resourceIcons(usages).instances shouldEqual 1
      resourceIcons(usages).elbs shouldEqual 2
      resourceIcons(usages).unknown shouldEqual 1
    }
  }

  "splitReportsBySuppressed" - {
    val flaggedA = SGOpenPortsDetail("Ok", "eu-west-1", "name-1", "sg-1", "vpc-1", "tcp", "1099", "Yellow", isSuppressed = false, None, None)
    val flaggedB = SGOpenPortsDetail("Ok", "eu-west-1", "name-2", "sg-2", "vpc-2", "tcp", "1099", "Yellow", isSuppressed = false, None, None)
    val suppressedA = SGOpenPortsDetail("Ok", "eu-west-1", "name-3", "sg-3", "vpc-3", "tcp", "1099", "Yellow", isSuppressed = true, None, None)
    val suppressedB = SGOpenPortsDetail("Ok", "eu-west-1", "name-4", "sg-4", "vpc-4", "tcp", "1099", "Yellow", isSuppressed = true, None, None)

    "returns two lists when there are flagged and suppressed" in {
      val sgReport = List((flaggedA, Set[SGInUse]()), (suppressedA, Set[SGInUse]()), (flaggedB, Set[SGInUse]()), (flaggedA, Set[SGInUse]()))
      splitReportsBySuppressed(sgReport).suppressed.length shouldEqual 1
      splitReportsBySuppressed(sgReport).flagged.length shouldEqual 3
    }

    "will place SGs with suppressed reports into the suppressed list" in {
      val sgReport = List((suppressedA, Set[SGInUse]()), (flaggedA, Set[SGInUse]()), (suppressedB, Set[SGInUse]()))
      splitReportsBySuppressed(sgReport).suppressed shouldEqual List(
        (suppressedA, Set[SGInUse]())
        (suppressedB, Set[SGInUse]())
      )
    }
  }
}
