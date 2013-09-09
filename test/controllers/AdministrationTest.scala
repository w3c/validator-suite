package controllers

import play.api.test._
import play.api.test.Helpers._

import org.scalatest._
import org.scalatest.matchers._
import scala.concurrent.ExecutionContext.Implicits.global

class AdministrationTest extends WordSpec with MustMatchers {
  
  def authHeader(password: String): String = {
    val hex = org.apache.commons.codec.binary.Base64.encodeBase64(s"ROOT:${password}".getBytes)
    new String(hex)
  }

  "A user needs to authenticate with root.password when accessing /admin/" in {

    val password = "6ggFWMoTODiU"
    val headerValue = authHeader(password)

     running(FakeApplication()) {
       val post =
         route(FakeRequest(GET, "/admin/").withHeaders("Authorization" -> s"Basic ${headerValue}")).get
       status(post) must be (OK)
     }
    
  }

  "A user without the right credentials for accessing /admin/ should be rejected with a 401" in {

    val password = "clearly not the right password :-)"
    val headerValue = authHeader(password)
    
     running(FakeApplication()) {
       val post =
         route(FakeRequest(GET, "/admin/").withHeaders("Authorization" -> s"Basic ${headerValue}")).get
       status(post) must be (UNAUTHORIZED)
     }
    
  }

}
