package logic

import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import model.{AccessKey, AccessKeyEnabled, VulnerableAccessKey}

object VulnerableAccessKeys {

  def hasOutdatedHumanKey(keys: List[AccessKey]): Boolean =
    keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamHumanUserRotationCadence
      && key.keyStatus == AccessKeyEnabled)

  def hasOutdatedMachineKey(keys: List[AccessKey]): Boolean =
    keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamMachineUserRotationCadence
      && key.keyStatus == AccessKeyEnabled)


  /*
    * These two methods are used only for the IAM dashboard (credentials report display). It is intended to be
    * temporary, because eventually the logic for dashboard and scheduled job (IamJob) should be the same
  */
  def hasOutdatedHumanKeyIncludingDisabled(keys: List[AccessKey]): Boolean =
    keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamHumanUserRotationCadence)

  def hasOutdatedMachineKeyIncludedDisabled(keys: List[AccessKey]): Boolean =
    keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamMachineUserRotationCadence)

  def isOutdated(user: VulnerableAccessKey): Boolean = {
    if (user.humanUser) user.accessKeyWithId.accessKey.keyStatus == AccessKeyEnabled &&
      hasOutdatedHumanKey(List(user.accessKeyWithId.accessKey))
    else user.accessKeyWithId.accessKey.keyStatus == AccessKeyEnabled &&
      hasOutdatedMachineKey(List(user.accessKeyWithId.accessKey))
  }
}
