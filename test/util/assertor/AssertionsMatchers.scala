package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers._
import org.w3.vs.model._

trait AssertionsMatchers {

  import scala.collection.GenTraversable

  val haveErrors = new Matcher[GenTraversable[Assertion]] {
    def apply(assertions: GenTraversable[Assertion]) = {
      val b = assertions exists { _.severity == Error }
      val ok = s"assertions have errors"
      val ko = s"assertions have no error"
      MatchResult(b, ok, ko, ok, ko)
    }
  }

  
}
