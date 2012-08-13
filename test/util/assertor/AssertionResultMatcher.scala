package org.w3.vs.assertor

import org.specs2.mutable._
import org.specs2.matcher.Matcher
import org.specs2.matcher.Expectable
import org.w3.vs.model._

trait AssertionResultMatcher extends Specification {

  def haveErrors[Result <: AssertorResponse] = new Matcher[Result] {
    def apply[R <: Result](r: Expectable[R]) = {
      val bool = r.value match {
        case result: AssertorResult => result.errors > 0
        case _ => false
      }
      result(bool,
             r.description + " has errors",
             r.description + " has no error", r)
    }
  }
  
  def haveErrorz[Result <: Iterable[Assertion]] = new Matcher[Result] {
    def apply[R <: Result](r: Expectable[R]) = {
      val bool = r.value exists { _.severity == Error }
      result(bool,
             r.description + " has errors",
             r.description + " has no error", r)
    }
  }

  
}
