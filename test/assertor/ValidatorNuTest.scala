package org.w3.vs.assertor

import org.scalatest._
import org.scalatest.Matchers
import org.w3.vs.web._
import org.w3.vs.model._
import org.w3.vs.web.URL
import org.w3.vs.view.Helper
import java.io.File

object ValidatorNuTest {

  val cacheDirectory = {
    val f = new File("test/resources/cache")
    //println(f.getAbsolutePath)
    f
  }
  val cache = Cache(cacheDirectory)

}

class ValidatorNuTest extends WordSpec with Matchers with AssertionsMatchers with BeforeAndAfterAll {

  import ValidatorNuTest.cache

  val localValidators = new LocalValidators(2719, Some(cache))

  import localValidators.ValidatorNu

  override def beforeAll(): Unit = {
    localValidators.start()
  }
  
  override def afterAll(): Unit = {
    localValidators.stop()
  }

  "http://www.google.com should have at least one error" in {
    val url = URL("http://www.google.com")
    val assertions = ValidatorNu.assert(url, Map.empty: AssertorConfiguration)
    assertions should (haveErrors)
  }

//  "http://www.1stincoffee.com should have at least one info" in {
//    val url = URL("http://www.1stincoffee.com")
//    val assertions = ValidatorNu.assert(url, Map.empty: AssertorConfiguration)
//    println(assertions)
//  }



}
