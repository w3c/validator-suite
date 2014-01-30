/*
// Keep this class for reference for this testing framework.

package controllers

import play.api.test._
import play.api.test.Helpers._

import org.scalatest._
import org.scalatest.matchers._
import scala.concurrent.ExecutionContext.Implicits.global
import org.mindrot.jbcrypt.BCrypt

class AdministrationTest extends WordSpec with Matchers {
  
  def authHeader(password: String): String = {
    val hex = org.apache.commons.codec.binary.Base64.encodeBase64(s"ROOT:${password}".getBytes)
    new String(hex)
  }

  "A user needs to authenticate with root.password when accessing /admin/" in {

    val password = "foobar"
    val headerValue = authHeader(password)
    val configuration = Map("root.password" -> BCrypt.hashpw(password, BCrypt.gensalt()))

     running(FakeApplication(additionalConfiguration = configuration)) {
       val post =
         route(FakeRequest(GET, "/admin/").withHeaders("Authorization" -> s"Basic ${headerValue}")).get
       status(post) should be (OK)
     }
    
  }

  "A user without the right credentials for accessing /admin/ should be rejected with a 401" in {

    val password = "clearly not the right password :-)"
    val headerValue = authHeader(password)
    
     running(FakeApplication()) {
       val get =
         route(FakeRequest(GET, "/admin/").withHeaders("Authorization" -> s"Basic ${headerValue}")).get
       status(get) should be (UNAUTHORIZED)
       headers(get)("WWW-Authenticate") should be("""Basic realm="W3C Validator Suite"""")
     }
    
  }

}
*/
