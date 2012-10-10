package org.w3.vs.assertor

import org.w3.util.URL
import org.w3.vs.view.Helper
import akka.util.duration._
import org.scalatest._
import org.scalatest.matchers._
import org.w3.vs.model._

trait ErrorMatchers {

  val haveError: Matcher[Traversable[Assertion]] = new Matcher[Traversable[Assertion]] {
    def apply(left: Traversable[Assertion]) = {
      val failureMessageSuffix = "found errors"
      val negatedFailureMessageSuffix = "didnt find errors"

      MatchResult(
        left exists { _.severity == Error },
        failureMessageSuffix,
        negatedFailureMessageSuffix,
        failureMessageSuffix,
        negatedFailureMessageSuffix
      )

    }
  }

}

class CSSValidatorTest extends WordSpec with MustMatchers with BeforeAndAfterAll with ErrorMatchers {

  val localValidators = new LocalValidators(2719)

  import localValidators.CSSValidator

  override def beforeAll(): Unit = {
    localValidators.start()
  }
  
  override def afterAll(): Unit = {
    localValidators.stop()
  }

  "there should be no CSS error in http://www.w3.org/2011/08/validator-test/no-error.css" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.css")
    val assertion: Iterable[Assertion] = CSSValidator.assert(url, Map.empty)
    assertion must not (haveError)
  }

  "CSSValidator must accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = CSSValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine must startWith(CSSValidator.serviceUrl)

    val queryString: String = urlForMachine.substring(CSSValidator.serviceUrl.length)

    Helper.parseQueryString(queryString) must be (assertorConfiguration
      + ("output" -> List("ucn"))
      + ("uri" -> List(Helper.encode(url)))
    )

  }

}
