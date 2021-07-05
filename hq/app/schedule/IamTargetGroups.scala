package schedule

import model.{IAMAlertTargetGroup, Tag, VulnerableUser}

object IamTargetGroups {
    /**
    * Takes users with outdated keys/missing mfa and groups them based off the stack/stage/app tags of the users.
    * Produce a group containing the two groups of users (outdated keys/missing mfa) and a list of Anghammarad Targets
    * for alerts about those users to be sent to
    * @param vulnerableUsers
    * @return
    */
  def getNotificationTargetGroups(vulnerableUsers: Seq[VulnerableUser]): Seq[IAMAlertTargetGroup] = {
    val ssaStrings = vulnerableUsers.map(k => Tag.tagsToSSAID(k.tags)).distinct
    val groups = vulnerableUsers.groupBy(u => Tag.tagsToSSAID(u.tags))

    // merge groups into IAMAlertTargetGroup seq
    ssaStrings.map { ssaString =>
      val users =  groups.getOrElse(ssaString, Seq())

      // assume that within a group all tags are the same. Use first element of the group to generate tags
      val targets = users.headOption.map(k => Tag.tagsToAnghammaradTargets(k.tags)).getOrElse(List())

      IAMAlertTargetGroup(targets, users)
    }
  }
}
