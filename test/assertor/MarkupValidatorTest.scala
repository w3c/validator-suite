package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.w3.vs.http._
import org.w3.vs.model._
import org.w3.vs.util.URL
import org.w3.vs.view.Helper
import java.io.File

object MarkupValidatorTest {

  val cacheDirectory = new File("test/resources/cache")
  val cache = Cache(cacheDirectory)

}

class MarkupValidatorTest extends WordSpec with MustMatchers with AssertionsMatchers with BeforeAndAfterAll {

  import MarkupValidatorTest.cache

  override def beforeAll(): Unit = {
    cache.setAsDefaultCache()
  }
  
  override def afterAll(): Unit = {
    cache.restorePreviousCache()
  }

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions = MarkupValidator.assert(url, Map.empty: AssertorConfiguration)
    assertions must (haveErrors)
  }

  "MarkupValidator must accept optional parameters" in {
    val url = URL("http://www.google.com")
    val assertorConfiguration: AssertorConfiguration = Map.empty

    val urlForMachine = MarkupValidator.validatorURLForMachine(url, assertorConfiguration).toString

    urlForMachine must startWith(MarkupValidator.configuration.serviceUrl)

    val queryString: String = urlForMachine.substring(MarkupValidator.configuration.serviceUrl.length)

    Helper.parseQueryString(queryString) must be (assertorConfiguration
      + ("output" -> List("ucn"))
      + ("uri" -> List(url.encode("UTF-8")))
    )

  }

  "http://www.w3.org/2008/MW4D/ should not be valid because it's using HTML5" in {
    MarkupValidator.configuration match {
      case _: Distant => ()
      case _: Local =>
        val url = URL("http://www.w3.org/2008/MW4D/")
        val assertions: Iterable[Assertion] = MarkupValidator.assert(url, Map.empty)
        assertions must have size(1)
        val assertion = assertions.head
        assertion.title must be(MarkupValidator.UsesHtml5Syntax)
    }
  }


  // "there should be no HTML error in http://www.w3.org/2011/08/validator-test/no-error.html" in {
  //   val url = URL("http://www.w3.org/2011/08/validator-test/no-error.html")
  //   val assertion = MarkupValidator.assert(url) getOrElse sys.error("was not a Success")
  //   assertion must not (haveErrorz)
  // }

}
