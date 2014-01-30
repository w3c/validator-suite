package org.w3.vs.assertor

import org.w3.vs.web.URL
import org.w3.vs.view.Helper
import org.scalatest._
import org.scalatest.matchers._
import org.w3.vs.model._
import org.w3.vs.web._
import java.io.File

object CSSValidatorTest {

  val cacheDirectory = new File("test/resources/cache")
  val cache = Cache(cacheDirectory)

  // to invoke:
  //   test:run-main org.w3.vs.assertor.CSSValidatorTest
  def main(args: Array[String]): Unit = {
    cache.reset()
    cache.retrieveAndCache(URL("http://www.google.com"), GET)
    cache.retrieveAndCache(URL("http://www.1stincoffee.com"), HEAD)
    cache.retrieveAndCache(URL("http://www.1stincoffee.com/"), GET)
    cache.retrieveAndCache(URL("http://www.google.com/"), GET)
    cache.retrieveAndCache(URL("http://www.w3.org/2011/08/validator-test/no-error.css"), GET)
    cache.retrieveAndCache(URL("http://www.w3.org/2008/MW4D/"), GET)
  }

}

class CSSValidatorTest extends WordSpec with Matchers with BeforeAndAfterAll with AssertionsMatchers {

  import CSSValidatorTest.cache

  val localValidators = new LocalValidators(2719, Some(cache))

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
    assertion should not (haveErrors)
  }

  "CSSValidator should accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = CSSValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine should startWith(CSSValidator.serviceUrl)

    val queryString: String = urlForMachine.substring(CSSValidator.serviceUrl.length)

    Helper.parseQueryString(queryString) should be (assertorConfiguration
      + ("output" -> List("ucn"))
      + ("uri" -> List(url.encode("UTF-8")))
      + ("vextwarning" -> List("true"))
    )

  }

}
