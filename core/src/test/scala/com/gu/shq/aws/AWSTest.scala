package com.gu.shq.aws

import com.gu.shq.aws.AWS
import com.gu.shq.model.AwsAccount
import com.gu.shq.utils.attempt.AttemptValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class AWSTest extends AnyFreeSpec with Matchers with AttemptValues {

  "real clients" - {

    val accounts = List(
      AwsAccount("test1", "Test1", "", ""),
      AwsAccount("test2", "Test2", "", "")
    )

    "iam" in {
      AWS.iamClients(accounts) should have size (accounts.size)
    }

  }

}
