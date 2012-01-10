package org.w3.vs.assertor

import org.specs2.mutable._
import org.specs2.matcher.Matcher
import org.specs2.matcher.Expectable
import org.w3.vs.model.Assertion

trait AssertionMatcher extends Specification {

  def haveErrors[Ass <: Assertion] = new Matcher[Ass] {
    def apply[A <: Ass](ass: Expectable[A]) = {
      result(ass.value.hasError,
             ass.description + " has errors",
             ass.description + " has no error", ass)
    }
  }

  
  
}