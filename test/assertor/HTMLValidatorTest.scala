package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.w3.vs.model._
import org.w3.util.URL
import org.w3.vs.view.Helper

class HTMLValidatorTest extends WordSpec with MustMatchers with AssertionResultMatcher {

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions: Iterable[Assertion] = HTMLValidator.assert(url, Map.empty)
    assertions must (haveErrors)
  }

  "HTMLValidator must accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = HTMLValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine must startWith(HTMLValidator.serviceUrl)

    val queryString: String = urlForMachine.substring(HTMLValidator.serviceUrl.length)

    Helper.parseQueryString(queryString) must be (assertorConfiguration
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
