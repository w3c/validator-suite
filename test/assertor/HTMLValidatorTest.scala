package org.w3.vs.assertor

import org.w3.util.URL

import org.specs2.mutable._

object HTMLValidatorTest extends Specification with AssertionMatcher {

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val observation = HTMLValidator.assert(url).get
    observation must (haveErrors)
  }

  "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
    val observation = HTMLValidator.assert(url).get
    observation must not (haveErrors)
  }

}
