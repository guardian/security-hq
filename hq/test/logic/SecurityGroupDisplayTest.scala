package logic

import model.{ELB, Instance, SGInUse, UnknownUsage}
import org.scalatest.{FreeSpec, Matchers}
import SecurityGroupDisplay._


class SecurityGroupDisplayTest extends FreeSpec with Matchers {
  "resourceIcons" - {
    "returns 2 when there are two instances" in {
      val usages = List[SGInUse](Instance("instance-1"), Instance("instance-2"))
      resourceIcons(usages).instances shouldEqual 2
    }

    "returns individual counts for the different resource types" in {
      val usages = List[SGInUse](Instance("instance"), ELB("elb-1"), ELB("elb-2"), UnknownUsage("unknown", ""))
      resourceIcons(usages).instances shouldEqual 1
      resourceIcons(usages).elbs shouldEqual 2
      resourceIcons(usages).unknown shouldEqual 1
    }
  }
}
