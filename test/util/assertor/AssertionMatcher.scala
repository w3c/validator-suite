package org.w3.vs.assertor

import org.specs2.mutable._
import org.specs2.matcher.Matcher
import org.specs2.matcher.Expectable
import org.w3.vs.model.{Assertion, Events}

trait AssertionMatcher extends Specification {

  def haveErrors[Ass <: Assertion] = new Matcher[Ass] {
    def apply[A <: Ass](ass: Expectable[A]) = {
      val bool = ass.value match {
        case Assertion(_, _, events: Events) => events.hasError
        case _ => false
      }
      result(bool,
             ass.description + " has errors",
             ass.description + " has no error", ass)
    }
  }
  
  
}