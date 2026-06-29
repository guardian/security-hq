package model

import com.gu.anghammarad.models.{App, Stack, Stage => AnghammaradStage}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class TagTest extends AnyFreeSpec with Matchers {
  val tagsListWithStack = List(Tag("stAck", "pawnee"), Tag("department", "parks and recreation"))
  val tagsListWithStackStageApp = List(Tag("stack", "pawnee"), Tag("stage", "enquiry"), Tag("app", "the-pit"))


  "findAnghammaradTarget should locate available target tag regardless of case" in {
    Tag.findAnghammaradTarget("stack", Stack.apply, tagsListWithStack) shouldEqual Some(Stack("pawnee"))
    Tag.findAnghammaradTarget("STACK", Stack.apply, tagsListWithStack) shouldEqual Some(Stack("pawnee"))
    Tag.findAnghammaradTarget("blah", Stack.apply, tagsListWithStack) shouldEqual None
  }

  "tagsToAnghammaradTargets should convert tags to targets" in {
    Tag.tagsToAnghammaradTargets(tagsListWithStackStageApp) shouldEqual List(Stack("pawnee"), AnghammaradStage("enquiry"), App("the-pit"))
    Tag.tagsToAnghammaradTargets(tagsListWithStack) shouldEqual List(Stack("pawnee"))
  }

  "tagsToSSAID should correctly convert tags to a string" in {
    Tag.tagsToSSAID(tagsListWithStackStageApp) shouldEqual "the-pit-pawnee-enquiry"
    Tag.tagsToSSAID(List(Tag("venue", "entertainment 720"))) shouldEqual Tag.EMPTY_SSAID
    Tag.tagsToSSAID(tagsListWithStack) shouldEqual "pawnee"
  }

}