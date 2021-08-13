package logic

import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import model.{AccessKey, AccessKeyEnabled, VulnerableAccessKey}

object VulnerableAccessKeys {
  def hasOutdatedHumanKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamHumanUserRotationCadence)

  def hasOutdatedMachineKey(keys: List[AccessKey]): Boolean = keys.exists(key => DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > iamMachineUserRotationCadence)

  def isOutdated(user: VulnerableAccessKey): Boolean = {
    if (user.humanUser) user.accessKeyWithId.accessKey.keyStatus == AccessKeyEnabled &&
      hasOutdatedHumanKey(List(user.accessKeyWithId.accessKey))
    else user.accessKeyWithId.accessKey.keyStatus == AccessKeyEnabled &&
      hasOutdatedMachineKey(List(user.accessKeyWithId.accessKey))
  }
}
