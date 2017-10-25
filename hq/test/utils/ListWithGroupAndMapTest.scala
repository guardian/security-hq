package utils

import org.scalatest.{FreeSpec, Matchers}
import org.scalatest.prop.Checkers
import org.scalacheck.Prop._
import org.scalacheck.ScalacheckShapeless._
import RichList._


class ListWithGroupAndMapTest extends FreeSpec with Matchers with Checkers {
  case class TestObj(s: String, n: Int)

  "behaves like groupBy when given the identity mapping function" - {
    "for any input" in {
      check { (ts: List[TestObj]) =>
        ts.groupAndMap(_.s)(identity) == ts.groupBy(_.s)
      }
    }

    "for trivial non-empty case" in {
      List(TestObj("", 0)).groupAndMap(_.s)(identity) shouldEqual Map("" -> List(TestObj("", 0)))
    }

    "with duplicate entries" in {
      List(TestObj("", 0), TestObj("", 0)).groupAndMap(_.s)(identity) shouldEqual Map("" -> List(TestObj("", 0), TestObj("", 0)))
    }
  }

  "resulting map's keys are the same as unique values created by key function" in {
    check { (ts: List[TestObj], fn: Function1[TestObj, String]) =>
      ts.groupAndMap(fn)(identity).keys.toSet == ts.map(fn).toSet
    }
  }

  "Groupings contain precisely the mapped values" - {
    "for any input" in {
      check { (ts: List[TestObj], fn: Function1[TestObj, String]) =>
        val resultingValues = ts.groupAndMap(_.s)(fn).flatMap(_._2).toSet
        resultingValues == ts.map(fn).toSet
      }
    }
  }
}
