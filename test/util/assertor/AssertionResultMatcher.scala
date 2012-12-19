package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers._
import org.w3.vs.model._

trait AssertionResultMatcher {

  val haveErrors = new Matcher[Traversable[Assertion]] {
    def apply(assertions: Traversable[Assertion]) = {
      val b = assertions exists { _.severity == Error }
      val ok = s"assertions have errors"
      val ko = s"assertions have no error"
      MatchResult(b, ok, ko, ok, ko)
    }
  }

  
}
