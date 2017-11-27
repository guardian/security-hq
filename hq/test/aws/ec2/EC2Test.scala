package aws.ec2

import com.amazonaws.services.ec2.model.{DescribeNetworkInterfacesResult, GroupIdentifier, NetworkInterface, NetworkInterfaceAttachment}
import model._
import org.scalacheck.Gen
import org.scalacheck.Prop._
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.prop.{Checkers, PropertyChecks}
import org.scalatest.{FreeSpec, Matchers}

import scala.collection.JavaConverters._
import scala.util.Random


class EC2Test extends FreeSpec with Matchers with Checkers with PropertyChecks  {
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

  "sortAccountByFlaggedSgs" - {
    "puts accounts with nonempty flagged results above errors and empty results" in {
      check { (results: List[(AwsAccount, Either[Int, List[Int]])]) =>
        val resultsWithoutNonEmptyPrefix = EC2.sortAccountByFlaggedSgs(results).dropWhile {
          case (_, Right(sgs)) => sgs.nonEmpty
          case _ => false
        }
        // should be no nonEmpty flagged results in the rest of the list
        resultsWithoutNonEmptyPrefix.forall {
          case (_, Right(sgs)) if sgs.nonEmpty => false
          case _ => true
        }
      }
    }

    "puts errors below nonEmpty results and above empty" in {
      check { (results: List[(AwsAccount, Either[Int, List[Int]])]) =>
        val resultsWithoutNonEmptyPrefix = EC2.sortAccountByFlaggedSgs(results).dropWhile {
          case (_, Right(sgs)) => sgs.nonEmpty
          case _ => false
        }
        val resultsWithoutEmptyTail = resultsWithoutNonEmptyPrefix.reverse.dropWhile {
          case (_, Right(sgs)) => sgs.isEmpty
          case _ => false
        }
        // should be left with only the errors
        resultsWithoutEmptyTail.forall { case (_, result) => result.isLeft }
      }
    }

    "puts error empty flagged results below errors and non-empty results" in {
      check { (results: List[(AwsAccount, Either[Int, List[Int]])]) =>
        val resultsWithoutNonEmptyPrefix = EC2.sortAccountByFlaggedSgs(results).dropWhile {
          case (_, Right(sgs)) => sgs.nonEmpty
          case _ => false
        }
        val resultsWithoutNonEmptyPrefixOrErrorsMiddle = resultsWithoutNonEmptyPrefix.dropWhile {
          case (_, Left(_)) => true
          case _ => false
        }
        resultsWithoutNonEmptyPrefixOrErrorsMiddle.forall {
          case (_, Right(Nil)) => true
          case _ => false
        }
      }
    }

    "sorts accounts by the number of flagged resources, *decreasing*" in {
      check { (results: List[(AwsAccount, List[Int])]) =>
        val successfulResults = results.map { case (account, flagged) => account -> Right(flagged)}
        val sortedResults = EC2.sortAccountByFlaggedSgs(successfulResults)
        sortedResults == sortedResults.sortBy {
          case (account, Right(items)) =>
            items.length * -1 // decreasing
          case _ =>
            throw new TestFailedException("Generated invalid test case - should only have successful results with different numbers of items in the Right", 10)
        }
      }
    }
  }

  "security group" - {
    "sort by usage" in {
      val sgsOpenPorts = for {
        status <- Gen.oneOf("Ok", "Warning", "Error")
        name <- Gen.alphaStr
        id <- Gen.alphaStr
        vpcId <- Gen.alphaStr
        port <- Gen.oneOf(0 to 65000)
      } yield SGOpenPortsDetail(status, "eu-west-1", name, id, vpcId, "tcp", port.toString, "Yellow", false )


      forAll(Gen.listOf(sgsOpenPorts)) { detail =>
        val sgsUsageMap = detail.map { sgs =>
          val usages = Seq[Set[SGInUse]](Set.empty, Set(Instance(sgs.id), ELB(sgs.id)), Set(Instance(sgs.id), ELB(sgs.id), UnknownUsage("unknown", "nic-1")))
          sgs.id -> usages(Random.nextInt(3))
        }.toMap

        val sortedResult = EC2.sortSecurityGroupsByInUse(detail, sgsUsageMap)
        sortedResult should be(sortedResult.sortWith { case ((_, s1), (_, s2)) => s1.size > s2.size })
      }
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
