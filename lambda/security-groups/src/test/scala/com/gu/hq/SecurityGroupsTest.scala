package com.gu.hq

import com.amazonaws.services.elasticloadbalancing.model.{DescribeLoadBalancersResult, LoadBalancerDescription}
import com.gu.hq.lambda.model.{IpPermission, SGConfiguration, UserIdGroupPair}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class SecurityGroupsTest extends AnyFreeSpec with Matchers {
  "openToWorld" - {
    "returns true for a SecurityGroup that is open" in {
      val openPermissions = List(
        IpPermission("tcp", Some(-1), Some(-1), Nil, List("0.0.0.0/0"), Nil)
      )
      val sg = SGConfiguration("owner", "security-group", "sg-01234", "Test SecurityGroup", openPermissions, Nil, "vpc-id", Nil)

      SecurityGroups.openToWorld(sg) shouldEqual true
    }

    "returns false for a SecurityGroup that has no rules" in {
      val ipPermsWithNoRules = List(
        IpPermission("tcp", Some(-1), Some(-1), Nil, Nil, Nil)
      )
      val sg = SGConfiguration("owner", "security-group", "sg-01234", "Test SecurityGroup", ipPermsWithNoRules, Nil, "vpc-id", Nil)

      SecurityGroups.openToWorld(sg) shouldEqual false
    }

    "returns false for a SecurityGroup that is not open" in {
      val closedPermissions = List(
        IpPermission("tcp", Some(-1), Some(-1), Nil, List("1.2.3.4/32"), Nil)
      )
      val sg = SGConfiguration("owner", "security-group", "sg-01234", "Test SecurityGroup", closedPermissions, Nil, "vpc-id", Nil)

      SecurityGroups.openToWorld(sg) shouldEqual false
    }

    "returns false for a SecurityGroup that allowes traffic from a named group" in {
      val ipPerm = List(
        IpPermission("tcp", Some(-1), Some(-1), List(UserIdGroupPair("uid", "gid", None, None, None, None)), Nil, Nil)
      )
      val sg = SGConfiguration("owner", "security-group", "sg-01234", "Test SecurityGroup", ipPerm, Nil, "vpc-id", Nil)

      SecurityGroups.openToWorld(sg) shouldEqual false
    }
  }

  "attachedToElb" - {
    val loadBalancers = new DescribeLoadBalancersResult()
      .withLoadBalancerDescriptions(
        elbWithSg("sg-123"),
        elbWithSg("sg-456", "sg-789")
      )
    val sgConf = SGConfiguration("owner", "sg name", "sg-id", "description", Nil, Nil, "vpc-id", Nil)

    "returns false if no ELBs use the provided security group" in {
      SecurityGroups.attachedToElb(
        sgConf.copy(groupId = "not-on-any-elb"),
        loadBalancers
      ) shouldEqual false
    }

    "returns true if an ELB uses the provided security group" in {
      SecurityGroups.attachedToElb(
        sgConf.copy(groupId = "sg-123"),
        loadBalancers
      ) shouldEqual true
    }

    "returns true if the security group is one of the groups an ELB uses" in {
      SecurityGroups.attachedToElb(
        sgConf.copy(groupId = "sg-789"),
        loadBalancers
      ) shouldEqual true
    }
  }

  private def elbWithSg(sgIds: String *): LoadBalancerDescription = {
    val elb = new LoadBalancerDescription()
    elb.withSecurityGroups(sgIds:_*)
    elb
  }
}
