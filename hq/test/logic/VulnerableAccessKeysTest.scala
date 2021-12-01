package logic

import logic.VulnerableAccessKeys._
import model.{AccessKey, AccessKeyDisabled, AccessKeyEnabled}
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}


class VulnerableAccessKeysTest extends FreeSpec with Matchers {
  def accessKey(daysOld: Int, active: Boolean) = {
    AccessKey(
      if (active) AccessKeyEnabled else AccessKeyDisabled,
      Some(DateTime.now().minusDays(daysOld))
    )
  }

  "hasOutdatedHumanKey" - {
    "returns true for an active and very old key" in {
      hasOutdatedHumanKey(List(accessKey(1000, true))) shouldEqual true
    }
    "returns false for an active fresh key" in {
      hasOutdatedHumanKey(List(accessKey(10, true))) shouldEqual false
    }

    "returns false for a disabled old key" in {
      hasOutdatedHumanKey(List(accessKey(1000, false))) shouldEqual false
    }
    "returns false for a disabled fresh key" in {
      hasOutdatedHumanKey(List(accessKey(10, false))) shouldEqual false
    }
  }

  "hasOutdatedMachineKey" - {
    "returns true for an active and very old key" in {
      hasOutdatedMachineKey(List(accessKey(1000, true))) shouldEqual true
    }
    "returns false for an active fresh key" in {
      hasOutdatedMachineKey(List(accessKey(10, true))) shouldEqual false
    }

    "returns false for a disabled old key" in {
      hasOutdatedMachineKey(List(accessKey(1000, false))) shouldEqual false
    }
    "returns false for a disabled fresh key" in {
      hasOutdatedMachineKey(List(accessKey(10, false))) shouldEqual false
    }
  }

  "hasOutdatedHumanKeyIncludingDisabled" - {
    "returns true for an active and very old key" in {
      hasOutdatedHumanKeyIncludingDisabled(List(accessKey(1000, true))) shouldEqual true
    }
    "returns false for an active fresh key" in {
      hasOutdatedHumanKeyIncludingDisabled(List(accessKey(10, true))) shouldEqual false
    }

    "returns true for a disabled old key" in {
      hasOutdatedHumanKeyIncludingDisabled(List(accessKey(1000, false))) shouldEqual true
    }
    "returns false for a disabled fresh key" in {
      hasOutdatedHumanKeyIncludingDisabled(List(accessKey(10, false))) shouldEqual false
    }
  }

  "hasOutdatedMachineKeyIncludingDisabled" - {
    "returns true for an active and very old key" in {
      hasOutdatedMachineKeyIncludingDisabled(List(accessKey(1000, true)))
    }
    "returns false for an active fresh key" in {
      hasOutdatedMachineKeyIncludingDisabled(List(accessKey(10, true))) shouldEqual false
    }

    "returns true for a disabled old key" in {
      hasOutdatedMachineKeyIncludingDisabled(List(accessKey(1000, false))) shouldEqual true
    }
    "returns false for a disabled fresh key" in {
      hasOutdatedMachineKeyIncludingDisabled(List(accessKey(10, false))) shouldEqual false
    }
  }
}
