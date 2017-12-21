package service

import com.gu.Box
import model.{AwsAccount, SGInUse, SGOpenPortsDetail}
import utils.attempt.{Attempt, FailedAttempt}

import scala.concurrent.ExecutionContext


object SecurityGroups {

  type FLAGGED_SGS = List[(AwsAccount, Either[FailedAttempt, List[(SGOpenPortsDetail, Set[SGInUse])]])]

  val sgsBox = Box[FLAGGED_SGS](List.empty)

  def getFlaggedSecurityGroups()(implicit ec: ExecutionContext): Attempt[FLAGGED_SGS] = Attempt.Right(sgsBox.get())

  def update(v: Attempt[FLAGGED_SGS])(implicit ec: ExecutionContext) = {
    v.map ( v => sgsBox.send(v))
  }

}
