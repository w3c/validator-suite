package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.w3.vs.http._
import org.w3.vs.model._
import org.w3.util.URL
import org.w3.vs.view.Helper
import java.io.File

object ValidatorNuTest {

  val cacheDirectory = new File("test/resources/cache")
  val cache = Cache(cacheDirectory)

}

class ValidatorNuTest extends WordSpec with MustMatchers with AssertionsMatchers with BeforeAndAfterAll {

  import ValidatorNuTest.cache

  val localValidators = new LocalValidators(2719, Some(cache))

  import localValidators.ValidatorNu

  override def beforeAll(): Unit = {
    localValidators.start()
  }
  
  override def afterAll(): Unit = {
    cache.restorePreviousCache()
  }

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions = ValidatorNu.assert(url, Map.empty)
    assertions must (haveErrors)
  }

}
