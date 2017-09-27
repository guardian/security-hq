package aws.ec2

import java.util

import com.amazonaws.services.ec2.model.{DescribeNetworkInterfacesResult, GroupIdentifier, NetworkInterface, NetworkInterfaceAttachment}
import model.{ELB, Instance, UnknownUsage}
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._


class EC2Test extends FreeSpec with Matchers {
  "parseNetworkInterface" - {
    "parses an ELB" in {
      EC2.parseNetworkInterface(elb("test-elb")) shouldEqual ELB("test-elb")
    }

    "parses an instance" in {
      EC2.parseNetworkInterface(instance("instance-id")) shouldEqual Instance("instance-id")
    }

    "parses something unexpected" in {
      val ni = new NetworkInterface()
        .withDescription("network-interface")
        .withNetworkInterfaceId("ni-123")
        .withAttachment(new NetworkInterfaceAttachment())
      EC2.parseNetworkInterface(ni) shouldEqual UnknownUsage("network-interface", "ni-123")
    }
  }

  "parseDescribeNetworkInterfacesResults" - {
    val niResult = new DescribeNetworkInterfacesResult()
      .withNetworkInterfaces(
        elb("elb-1", "sg-1"),
        elb("elb-2", "sg-2"),
        instance("i-1", "sg-3"),
        instance("i-2", "sg-3")
      )

    "returns nothing if no interfaces match search IDs" in {
      EC2.parseDescribeNetworkInterfacesResults(niResult, Nil) shouldBe empty
    }

    "returns matching subset of NIs" in {
      val result = EC2.parseDescribeNetworkInterfacesResults(niResult, List("sg-1", "sg-3"))
      result.values.flatten.toSet should contain only (ELB("elb-1"), Instance("i-1"), Instance("i-2"))
    }

    "keys should be only matching SG IDs" in {
      val result = EC2.parseDescribeNetworkInterfacesResults(niResult, List("sg-1", "sg-3", "sg-not-used"))
      result.keys should contain only ("sg-1", "sg-3")
    }
  }

  // helpers for creating test data

  private def elb(description: String, sgIds: String*) = {
    new NetworkInterface()
      .withDescription(description)
      .withAttachment(
        new NetworkInterfaceAttachment()
          .withInstanceOwnerId("amazon-elb")
      )
      .withGroups(groups(sgIds))
  }

  private def instance(id: String, sgIds: String*) = {
    new NetworkInterface()
      .withAttachment(
        new NetworkInterfaceAttachment()
          .withInstanceId(id)
      )
      .withGroups(groups(sgIds))
  }

  private def groups(sgIds: Seq[String]) = {
    sgIds.map { sgId =>
      new GroupIdentifier()
        .withGroupId(sgId)
    }.asJava
  }
}
