package org.w3.vs.assertor

import org.w3.util.URL
import org.specs2.mutable._

object CSSValidatorTest extends Specification with AssertionResultMatcher {

  "there should be no CSS error in http://www.w3.org/2011/08/validator-test/no-error.css" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.css")
    val assertion = CSSValidator.assert(url) getOrElse sys.error("was not a Success")
    assertion must not (haveErrors)
  }

}