package org.w3.vs.assertor

import org.w3.util.URL
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

  override def beforeAll(): Unit = {
    org.w3.vs.assertor.CSSValidator.start()
  }
  
  override def afterAll(): Unit = {
    org.w3.vs.assertor.CSSValidator.stop()
  }

  "there should be no CSS error in http://www.w3.org/2011/08/validator-test/no-error.css" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.css")
    val assertion: Iterable[Assertion] = CSSValidator.assert(url, Map.empty)
    assertion must not (haveError)
  }

}
