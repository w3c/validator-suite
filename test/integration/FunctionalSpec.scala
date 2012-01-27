package org.w3.vs.integration

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
        
        $("input[value=Validate]").click()

        //click("input[value=Validate]")
        
        import java.util.concurrent.TimeUnit._
        
        val now = System.currentTimeMillis()
        
        //Thread.sleep(3000L)
        
        // input[value=Stop]
        //await().atMost(5, SECONDS).until("#logs ul li[class=status]").hasSize(3)
        
        webDriver.manage().timeouts().implicitlyWait(10, SECONDS)
        
        
        
        //await().atMost(5, SECONDS).until("input").`with`("value").equalTo("Validate").hasSize(3)
        $("input[value=Stop]").click()
        
        val millis = System.currentTimeMillis() - now

        println("*** " + millis)

        println(pageSource.toString())
        click("input[value=Stop]")
        
        pageSource must contain ("GET (200) http://www.w3.org")
        
        
      }
    }

  }
  
}