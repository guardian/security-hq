package config

import software.amazon.awssdk.regions.Region

/** Play-free configuration values shared by `core` and `hq`. */
object CoreConfig {
  val iamHumanUserRotationCadence: Long = 90
  val iamMachineUserRotationCadence: Long = 365
  val outdatedCredentialOptOutUserTag = "SecurityHQ::OutdatedCredentialOptOut"
  val daysBetweenWarningAndFinalNotification = 7
  val daysBetweenFinalNotificationAndRemediation = 7

  // TODO fetch the region dynamically from the instance
  val region: Region = Region.of("eu-west-1")
}
