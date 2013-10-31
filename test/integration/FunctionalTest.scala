// TODO: investigate why htmlunit hangs on this test

package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import play.api.Play._
import play.api.test.TestServer
import org.w3.vs.util.timer._
import org.w3.vs.model.User
import org.w3.vs.Global

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

      // This user should register first through /register
      implicit val conf = Global.vs
      val testUser = User.create("Test User", "test@example.com", "secret", 100, false, false, false)
      testUser.save().getOrFail()

      goTo("http://localhost:9001/login")
      fill("#l_email").`with`("test@example.com")
      fill("#l_password").`with`("secret")
      $("#submit-login").isEmpty must be (false)
      click("#submit-login")

      url must be === "http://localhost:9001/jobs"
      $(".hello").first().getText must include ("Test User")

      goTo("http://localhost:9001/admin")
      $("h1").first().getText must include ("Page Not Found")

      // and log out
      goTo("http://localhost:9001/jobs")
      //click("a[data-dropdown=myAccount]")
      click("button.logout")

      url must be === "http://localhost:9001/"

      goTo("http://localhost:9001/login")
      url must be === "http://localhost:9001/login"
      $("#submit-login").isEmpty must be (false)

      // TODO. Define more functional tests: https://github.com/w3c/validator-suite/issues/271

      /*fill("#l_email").`with`("tgambet@w3.org") // Root
      fill("#l_password").`with`("secret")
      click("#submit-login")
      goTo("http://localhost:9001/admin")
      $("h1").first().getText() must not include ("404")*/

    }
  }
}

