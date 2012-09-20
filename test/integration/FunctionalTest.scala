package org.w3.vs.integration

import play.api.test._
import play.api.test.Helpers._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import play.api.Play._
import play.api.test.TestServer

class FunctionalTest extends WordSpec with MustMatchers with BeforeAndAfterAll {

  override def beforeAll(configMap: Map[String, Any]): Unit = {
    org.w3.vs.Main.main(Array())
  }

  "an Application should let a valid user log" in {
  
    running(TestServer(9001), HTMLUNIT) { browser =>
      Thread.sleep(2000)
      import browser._

      val baseUrl: String = "http://localhost:9001"

      goTo(baseUrl + controllers.routes.Jobs.index.toString)
      url must be === (baseUrl + controllers.routes.Jobs.index.toString)
      fill("#email").`with`("bertails@w3.org")
      fill("#password").`with`("secret")
      click("#submit-login")
      url must be === (baseUrl + controllers.routes.Jobs.index.toString)

      $("#admin .name").first().getText() must include ("Alexandre Bertails")       
      
      // and log out
      click("#admin form button")
      //goTo("http://localhost:9001/logout")
      url must be === (baseUrl + controllers.routes.Application.login.toString)
      goTo(baseUrl + controllers.routes.Jobs.index.toString)
      $("button#submit-login").isEmpty must be (false)
      
      // can't log in with wrong password
      fill("#email").`with`("bertails@w3.org")
      fill("#password").`with`("wrong")
      click("#submit-login")
      url must be === (baseUrl + controllers.routes.Application.login.toString)

    }
  }
}
