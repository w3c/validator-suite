package org.w3.vs.test

import play.api.test._
import play.api.test.Helpers._

import play.api.libs._

import org.specs2.mutable._

object FunctionalSpec extends Specification {

  "an Application" should {
    
    "pass functional test" in {
      running(TestServer(9001), HTMLUNIT) { browser =>
        
        import browser._
        
        goTo("http://localhost:9001")
        
        url must beEqualTo ("http://localhost:9001/login")
        
        pageSource must contain ("Default users")

        fill("#email").`with`("bertails@w3.org")
        fill("#password").`with`("secret")
        
        click("#submit-login")
        
        url must beEqualTo ("http://localhost:9001/")

        
      }
    }

  }
  
}