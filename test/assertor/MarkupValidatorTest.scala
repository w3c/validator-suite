package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.Matchers
import org.w3.vs.web._
import org.w3.vs.model._
import org.w3.vs.web.URL
import org.w3.vs.view.Helper
import java.io.File

object MarkupValidatorTest {

//  val cacheDirectory = new File("test/resources/cache")
//  val cache = Cache(cacheDirectory)

}

abstract class MarkupValidatorTest extends WordSpec with Matchers with AssertionsMatchers with BeforeAndAfterAll {

//  import MarkupValidatorTest.cache

  override def beforeAll(): Unit = {
//    cache.setAsDefaultCache()
  }
  
  override def afterAll(): Unit = {
//    cache.restorePreviousCache()
  }

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions = MarkupValidator.assert(url, Map.empty: AssertorConfiguration)
    assertions should (haveErrors)
  }

  "MarkupValidator should accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = MarkupValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine should startWith(MarkupValidator.serviceUrl)

    val queryString: String = urlForMachine.substring(MarkupValidator.serviceUrl.length)

    Helper.parseQueryString(queryString) should be (assertorConfiguration
      + ("output" -> List("ucn"))
      + ("uri" -> List(url.encode("UTF-8")))
    )

  }

  "http://www.w3.org/2008/MW4D/ should not be valid because it's using HTML5" in {
    if (! MarkupValidator.serviceUrl.startsWith("http://validator.w3.org")) {
      val url = URL("http://www.w3.org/2008/MW4D/")
      val assertions: Iterable[Assertion] = MarkupValidator.assert(url, Map.empty)
      assertions should have size(1)
      val assertion = assertions.head
      assertion.title should be(MarkupValidator.UsesHtml5Syntax)
    } else {
      play.Logger.debug("not executing test because using validator.w3.org")
    }
  }


  // "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
  //   val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
  //   val assertion = MarkupValidator.assert(url) getOrElse sys.error("was not a Success")
  //   assertion should not (haveErrorz)
  // }

}
