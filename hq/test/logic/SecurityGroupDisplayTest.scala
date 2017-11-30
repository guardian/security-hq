package logic

import model.{ELB, Instance, SGInUse, UnknownUsage}
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
      val usages = List[SGInUse](Instance("instance"), ELB("elb-1"), ELB("elb-2"), UnknownUsage("unknown", ""))
      resourceIcons(usages).instances shouldEqual 1
      resourceIcons(usages).elbs shouldEqual 2
      resourceIcons(usages).unknown shouldEqual 1
    }
  }
}
