package org.w3.vs.http

import org.w3.vs._
import org.scalatest._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util._
import akka.util.duration._
import org.w3.util.akkaext._

class HttpTest() extends RunTestHelper(new DefaultProdConfiguration { }) with Inside {
  
  val servers = Seq(Webserver(9001, Website.cyclic(10).toServlet))
  
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  "testing HEAD on existing URL" in {

    val context = (OrganizationId(), JobId(), RunId())

    http ! Fetch(URL("http://localhost:9001/"), HEAD, context)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/"))
      response.action must be(HEAD)
      response.status must be(200)
      //body must be === ""
      response.context must be(context)
    }

  }



  "testing GET on existing URL" in {

    val context = (OrganizationId(), JobId(), RunId())
    
    http ! Fetch(URL("http://localhost:9001/"), GET, context)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/"))
      response.action must be(GET)
      response.status must be(200)
      //body must not be ('empty)
      response.context must be(context)
    }

  }


  "testing HEAD on non-existing URL (404)" in {

    val context = (OrganizationId(), JobId(), RunId())
      
    http ! Fetch(URL("http://localhost:9001/404/foo"), HEAD, context)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/404/foo"))
      response.action must be(HEAD)
      response.status must be(404)
      response.context must be(context)
    }

  }

  
  if (System.getProperty("os.name") startsWith "Linux") {

    "testing HEAD on non-existing domain (foo.localhost)" in {
      
      val context = (OrganizationId(), JobId(), RunId())
      
      http ! Fetch(URL("http://foo.localhost/bar"), HEAD, context)
      
      val fetchResponse = expectMsgType[ResourceResponse](1.second)
      
      inside (fetchResponse) { case response: ErrorResponse =>
        response.url must be(URL("http://foo.localhost/bar"))
        response.action must be(HEAD)
        response.context must be(context)
      }

    }
  }

  "testing delays" in {

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(200)

    val context = (OrganizationId(), JobId(), RunId())

    for(i <- 1 to 100) {
      http ! Fetch(URL("http://localhost:9001/"+i), HEAD, context)
    }

    val fetchResponse = expectMsgType[ResourceResponse](1.second)
    
    implicit val timeout: akka.util.Timeout = 1.second

    def pendingFetches(): Int =
      (PathAware(http, http.path / "localhost_9001") ? HowManyPendingRequests).mapTo[Int].result(1.second).fold(f => throw f, s => s)

    pendingFetches() must be(99)

    val secondResponse =  expectMsgType[ResourceResponse](1.second)

    pendingFetches() must be(98)

  }


}

