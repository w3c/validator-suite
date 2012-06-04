package org.w3.vs.assertor

import org.w3.util.URL
import akka.util.Duration
import akka.util.duration._
import org.w3.vs.model.AssertionClosed
import org.scalatest._
import org.scalatest.matchers._

import org.w3.vs.model._

trait ErrorMatchers {

  val haveError: Matcher[Traversable[AssertionClosed]] = new Matcher[Traversable[AssertionClosed]] {
    def apply(left: Traversable[AssertionClosed]) = {
      val failureMessageSuffix = "found errors"
      val negatedFailureMessageSuffix = "didnt find errors"

      MatchResult(
        left exists { _.assertion.severity == Error },
        failureMessageSuffix,
        negatedFailureMessageSuffix,
        failureMessageSuffix,
        negatedFailureMessageSuffix
      )

    }
  }

}

class CSSValidatorTest extends WordSpec with MustMatchers with BeforeAndAfterAll with ErrorMatchers {

  override def beforeAll: Unit = {
    org.w3.vs.assertor.CSSValidator.start()
  }
  
  override def afterAll: Unit = {
    org.w3.vs.assertor.CSSValidator.stop()
  }

  "there should be no CSS error in http://www.w3.org/2011/08/validator-test/no-error.css" in {
    val url = URL("http://www.w3.org/2011/08/validator-test/no-error.css")
    val assertion: Iterable[AssertionClosed] = CSSValidator.assert(url, JobId(), RunId()).result(intToDurationInt(30).seconds).fold(f => throw f, s => s)
    assertion must not (haveError)
  }

}
