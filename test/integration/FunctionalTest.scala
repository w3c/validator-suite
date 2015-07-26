// TODO: investigate why htmlunit hangs on this test

package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatest.Matchers
import play.api.Play._
import play.api.test.TestServer
import org.w3.vs.util.timer._
import org.w3.vs.model.User
import org.w3.vs.Global

class FunctionalTest extends WordSpec with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    timer("load data for FunctionalTest") {
      org.w3.vs.Main.main(Array("default"))
    }
  }

  "an Application should let a valid user log" in {
  
    running(TestServer(9001), HTMLUNIT) { browser =>

      import browser._

      goTo("http://localhost:9001/jobs/")
      url should be ("http://localhost:9001/jobs")
      $("form[action='/login']").isEmpty should be (false)

      // This user should register first through /register
      implicit val conf = Global.vs
      val testUser = User.create("Test User", "test@example.com", "secret", 100, false, false, false)
      testUser.save().getOrFail()

      goTo("http://localhost:9001/login")
      fill("#l_email").`with`("test@example.com")
      fill("#l_password").`with`("secret")
      $("form[action='/login']").isEmpty should be (false)
      click("form[action='/login'] button")

      url should be ("http://localhost:9001/jobs")
      $(".hello").first().getText should include ("Test User")

      goTo("http://localhost:9001/admin")
      $("h1").first().getText should include ("Forbidden")

      // and log out
      goTo("http://localhost:9001/jobs")
      //click("a[data-dropdown=myAccount]")
      click("form[action='/logout'] button")

      url should be ("http://localhost:9001/")

      goTo("http://localhost:9001/login")
      url should be ("http://localhost:9001/login")
      $("form[action='/login']").isEmpty should be (false)

      // TODO. Define more functional tests: https://github.com/w3c/validator-suite/issues/271

    }
  }
}

