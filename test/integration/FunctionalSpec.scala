package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import play.api.libs._
import org.specs2.mutable._
import org.w3.util.localwebsite.LocalCache._

object FunctionalSpec extends Specification {

  "an Application" should {
    
    "should let a valid user log in" in {

      running(TestServer(9001), FIREFOX) { browser =>
        
        import browser._
        
        goTo("http://localhost:9001")
        
        url must beEqualTo ("http://localhost:9001/login")
        
        fill("#email").`with`("bertails@w3.org")
        fill("#password").`with`("secret")
        
        click("#submit-login")
        
        url must beEqualTo ("http://localhost:9001/")
        
        $("#admin").first().getText() must contain ("Alexandre Bertails")
        
      }
    }
  }
  
}