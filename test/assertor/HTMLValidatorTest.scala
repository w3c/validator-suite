package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.w3.vs.model._
import org.w3.util.URL
import org.w3.vs.view.Helper

class HTMLValidatorTest extends WordSpec with MustMatchers with AssertionsMatchers {

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions = HTMLValidator.assert(url, Map.empty)
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
      + ("uri" -> List(url.encode("UTF-8")))
    )

  }

  "http://www.w3.org/TR/html5 should not be valid because it's using HTML5" in {
    if (HTMLValidator.enable) {
      val url = URL("http://www.w3.org/TR/html5")
      val assertions: Iterable[Assertion] = HTMLValidator.assert(url, Map.empty)
      assertions must have size(1)
      val assertion = assertions.head
      assertion.title must be(HTMLValidator.UsesHtml5Syntax)
    }
  }


  // "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
  //   val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
  //   val assertion = HTMLValidator.assert(url) getOrElse sys.error("was not a Success")
  //   assertion must not (haveErrorz)
  // }

}
