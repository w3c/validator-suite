package org.w3.vs.assertor

import org.w3.util.URL
import org.specs2.mutable._
import akka.util.Duration
import akka.util.duration._
import org.w3.vs.model.AssertionClosed

object CSSValidatorTest extends Specification with AssertionResultMatcher {

  "there should be no CSS error in http://www.w3.org/2011/08/validator-test/no-error.css" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.css")
    val assertion: Iterable[AssertionClosed] = CSSValidator.assert(url).result(intToDurationInt(30).seconds).fold(f => throw f, s => s)
    assertion must not (haveErrorz)
  }

}