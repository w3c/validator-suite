// TODO: investigate why htmlunit hangs on this test

/*
package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import play.api.Play._
import play.api.test.TestServer
import org.w3.vs.util.Util._

class FunctionalTest extends WordSpec with MustMatchers with BeforeAndAfterAll {

  override def beforeAll(configMap: Map[String, Any]): Unit = {
    timer("load data for FunctionalTest") {
      org.w3.vs.Main.main(Array("default"))
    }
  }

  "an Application should let a valid user log" in {
  
    running(TestServer(9001), HTMLUNIT) { browser =>

      import browser._

      //val baseUrl: String = "http://localhost:9001/suite"

      goTo("http://localhost:9001/suite/jobs/")
      url must be === "http://localhost:9001/suite/jobs/"
      // and code should be Unauthenticated (not verifiable in this framework)

      fill("#email").`with`("bertails@w3.org")
      fill("#password").`with`("secret")
      click("#submit-login")
      url must be === "http://localhost:9001/suite/jobs/"

      $("#admin .name").first().getText() must include ("Alexandre Bertails")

      // and log out
      click("#admin form button")
      //goTo("http://localhost:9001/logout")
      url must be === "http://localhost:9001/suite/login"
      goTo("http://localhost:9001/suite/jobs/")
      $("button#submit-login").isEmpty must be (false)
      
      // can't log in with wrong password
      fill("#email").`with`("bertails@w3.org")
      fill("#password").`with`("wrong")
      click("#submit-login")
      url must be === ("http://localhost:9001/suite/login")

    }
  }
}
*/
