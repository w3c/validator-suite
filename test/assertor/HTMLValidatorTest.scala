package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.util.URL
import org.specs2.mutable._
import org.w3.vs.view.Helper

object HTMLValidatorTest extends Specification with AssertionResultMatcher {

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertion: Iterable[Assertion] = HTMLValidator.assert(url, Map.empty)
    assertion must (haveErrorz)
  }

  "HTMLValidator must accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = HTMLValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine must startWith(HTMLValidator.serviceUrl)

    val queryString: String = urlForMachine.substring(HTMLValidator.serviceUrl.length)

    Helper.parseQueryString(queryString) must beEqualTo (assertorConfiguration
      + ("output" -> List("ucn"))
      + ("uri" -> List(Helper.encode(url)))
    )

  }



  // "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
  //   val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
  //   val assertion = HTMLValidator.assert(url) getOrElse sys.error("was not a Success")
  //   assertion must not (haveErrorz)
  // }

}
