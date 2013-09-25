// TODO: investigate why htmlunit hangs on this test

package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import play.api.Play._
import play.api.test.TestServer
import org.w3.vs.util.timer._

class FunctionalTest extends WordSpec with MustMatchers with BeforeAndAfterAll {

  override def beforeAll(configMap: Map[String, Any]): Unit = {
    timer("load data for FunctionalTest") {
      org.w3.vs.Main.main(Array("default"))
    }
  }

  "an Application should let a valid user log" in {
  
    running(TestServer(9001), HTMLUNIT) { browser =>

      import browser._

      goTo("http://localhost:9001/jobs/")
      url must be === "http://localhost:9001/jobs"
      $("#submit-login").isEmpty must be (false)

      goTo("http://localhost:9001/login")
      fill("#l_email").`with`("tgambet+1@w3.org") // Non root
      fill("#l_password").`with`("secret")
      $("#submit-login").isEmpty must be (false)
      click("#submit-login")

      url must be === "http://localhost:9001/jobs"
      $(".loggedIn").first().getText() must include ("Thomas Gambet")

      goTo("http://localhost:9001/admin")
      $("h1").first().getText() must include ("404")

      // and log out
      goTo("http://localhost:9001/jobs")
      //click("a[data-dropdown=myAccount]")
      click("button.logout")

      url must be === "http://localhost:9001/"

      goTo("http://localhost:9001/login")
      url must be === "http://localhost:9001/login"
      $("#submit-login").isEmpty must be (false)

      fill("#l_email").`with`("tgambet@w3.org") // Root
      fill("#l_password").`with`("secret")
      click("#submit-login")
      goTo("http://localhost:9001/admin")
      $("h1").first().getText() must not include ("404")

    }
  }
}

