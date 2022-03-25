package logic

import config.Config.{iamHumanUserRotationCadence, iamMachineUserRotationCadence}
import model.{AccessKey, AccessKeyEnabled}

object VulnerableAccessKeys {
  /**
    * Flags active outdated keys for human users. Disabled keys are ignored.
    */
  def hasOutdatedHumanKey(keys: List[AccessKey]): Boolean =
    hasOutdatedKey(keys, iamHumanUserRotationCadence, flagInactiveKeys = false)

  /**
    * Flags active outdated keys for machine users. Disabled keys are ignored.
    */
  def hasOutdatedMachineKey(keys: List[AccessKey]): Boolean =
    hasOutdatedKey(keys, iamMachineUserRotationCadence, flagInactiveKeys = false)

  /*
   * These two methods are used only for the IAM dashboard (credentials report display). It is intended to be
   * temporary, because eventually the logic for dashboard and scheduled job (IamJob) should be the same
   */

  /**
    * Flags outdated human keys. The dashboard flags keys even when they are disabled.
    */
  def hasOutdatedHumanKeyIncludingDisabled(keys: List[AccessKey]): Boolean =
    hasOutdatedKey(keys, iamHumanUserRotationCadence, flagInactiveKeys = true)

  /**
    * Flags outdated machine keys. The dashboard flags keys even when they are disabled.
    */
  def hasOutdatedMachineKeyIncludingDisabled(keys: List[AccessKey]): Boolean =
    hasOutdatedKey(keys, iamMachineUserRotationCadence, flagInactiveKeys = true)

  private def hasOutdatedKey(keys: List[AccessKey], thresholdInDays: Long, flagInactiveKeys: Boolean): Boolean =
    keys.exists { key =>
      val outdated = DateUtils.dayDiff(key.lastRotated).getOrElse(1L) > thresholdInDays
      val active = key.keyStatus == AccessKeyEnabled
      outdated && (active || flagInactiveKeys)
    }
}
