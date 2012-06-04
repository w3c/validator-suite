package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.util.URL
import org.specs2.mutable._
import akka.util.Duration
import akka.util.duration._
import org.w3.vs.model.AssertionClosed

object HTMLValidatorTest extends Specification with AssertionResultMatcher {

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertion: Iterable[AssertionClosed] = HTMLValidator.assert(url, JobId(), RunId()).result(intToDurationInt(30).seconds).fold(f => throw f, s => s)
    assertion must (haveErrorz)
  }

  // "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
  //   val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
  //   val assertion = HTMLValidator.assert(url) getOrElse sys.error("was not a Success")
  //   assertion must not (haveErrorz)
  // }

}
