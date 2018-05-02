package com.gu.hq

import com.gu.hq.lambda.model.InvokingEvent

object Events {

  private[hq] def relevance(invokingEvent: InvokingEvent): Relevance = {
    if (
      invokingEvent.configurationItemDiff.exists { diff =>
        diff.changedProperties.keys.exists(_.contains("Configuration.IpPermissions"))
      }
      &&
      invokingEvent.configurationItemDiff.exists { diff =>
        Set("CREATE", "UPDATE").contains(diff.changeType)
      }
    )
      Relevant
    else
      NotRelevant
  }

  sealed trait Relevance
  case object Relevant extends Relevance
  case object NotRelevant extends Relevance
}
