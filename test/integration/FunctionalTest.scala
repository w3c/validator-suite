package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import play.api.libs._
import org.w3.util.localwebsite.LocalCache._
import org.scalatest._
import org.scalatest.matchers.MustMatchers

class FunctionalTest extends WordSpec with MustMatchers {

  "an Application" should {
  
    "should let a valid user log in" in {
        
      running(TestServer(9001), HTMLUNIT) { browser =>
        
        import browser._
        
        goTo("http://localhost:9001/dashboard")
        url must be === ("http://localhost:9001/dashboard")
        fill("#email").`with`("bertails@w3.org")
        fill("#password").`with`("secret")
        click("#submit-login")
        url must be === ("http://localhost:9001/dashboard")

        $("#admin").first().getText() must include ("Alexandre Bertails")       
        
        // and log out
        goTo("http://localhost:9001/logout")
        url must be === ("http://localhost:9001/login")
        goTo("http://localhost:9001/dashboard")
        $("form[action='/login']").isEmpty must be (false)
        
        // can't log in with wrong password
        fill("#email").`with`("bertails@w3.org")
        fill("#password").`with`("wrong")
        click("#submit-login")
        url must be === ("http://localhost:9001/login")
      }
    }
  }
}
