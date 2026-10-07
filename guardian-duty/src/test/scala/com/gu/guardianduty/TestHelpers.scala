package com.gu.guardianduty

import munit.diff.DiffOptions
import munit.{Assertions, Clues, Location}

trait TestHelpers extends Assertions {
  extension [L, R](either: Either[L, R]) {
    def leftValue(
        clue: => Any = "Expected Left but got Right"
    )(using Location, DiffOptions): L = {
      val (_, _) = munitCaptureClues(either.isLeft)
      either.fold(
        identity,
        r => fail(munitPrint(clue), Clues.fromValue(r))
      )
    }
    def rightValue(
        clue: => Any = "Expected Right but got Left"
    )(using Location, DiffOptions): R = {
      val (_, _) = munitCaptureClues(either.isRight)
      either.fold(
        l => fail(munitPrint(clue), Clues.fromValue(l)),
        identity
      )
    }
  }
}
