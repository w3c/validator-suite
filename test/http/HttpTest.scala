package org.w3.vs.http

import org.w3.vs._
import org.scalatest._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util._
import akka.util.duration._
import org.w3.util.akkaext._
import Http._

class HttpTest extends RunTestHelper with Inside {
  
  val servers = Seq(Webserver(9001, Website.cyclic(10).toServlet))
  
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  "testing HEAD on existing URL" in {

    val context = (OrganizationId(), JobId(), RunId())

    http ! Fetch(URL("http://localhost:9001/"), HEAD, context)

    val fetchResponse = expectMsgType[(RunId, ResourceResponse)](3.seconds)

    inside (fetchResponse) { case (contextRun: RunId, response: HttpResponse) =>
      response.url must be(URL("http://localhost:9001/"))
      response.action must be(HEAD)
      response.status must be(200)
      //body must be === ""
      contextRun must be(context._3)
    }

  }



  "testing GET on existing URL" in {

    val context = (OrganizationId(), JobId(), RunId())
    
    http ! Fetch(URL("http://localhost:9001/"), GET, context)

    val fetchResponse = expectMsgType[(RunId, ResourceResponse)](3.seconds)

    inside (fetchResponse) { case (contextRun: RunId, response: HttpResponse) =>
      response.url must be(URL("http://localhost:9001/"))
      response.action must be(GET)
      response.status must be(200)
      //body must not be ('empty)
      contextRun must be(context._3)
    }

  }


  "testing HEAD on non-existing URL (404)" in {

    val context = (OrganizationId(), JobId(), RunId())
      
    http ! Fetch(URL("http://localhost:9001/404/foo"), HEAD, context)

    val fetchResponse = expectMsgType[(RunId, ResourceResponse)](3.seconds)

    inside (fetchResponse) { case (contextRun: RunId, response: HttpResponse) =>
      response.url must be(URL("http://localhost:9001/404/foo"))
      response.action must be(HEAD)
      response.status must be(404)
      contextRun must be(context._3)
    }

  }

  
  if (System.getProperty("os.name") startsWith "Linux") {

    "testing HEAD on non-existing domain (foo.localhost)" in {
      
      val context = (OrganizationId(), JobId(), RunId())
      
      http ! Fetch(URL("http://foo.localhost/bar"), HEAD, context)
      
      val fetchResponse = expectMsgType[(RunId, ResourceResponse)](3.seconds)
      
      inside (fetchResponse) { case (contextRun: RunId, response: ErrorResponse) =>
        response.url must be(URL("http://foo.localhost/bar"))
        response.action must be(HEAD)
        contextRun must be(context._3)
      }

    }
  }

  "testing delays" in {

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(200)

    val context = (OrganizationId(), JobId(), RunId())

    for(i <- 1 to 100) {
      http ! Fetch(URL("http://localhost:9001/"+i), HEAD, context)
    }

    val fetchResponse = expectMsgType[(RunId, ResourceResponse)](3.seconds)
    
    implicit val timeout: akka.util.Timeout = 3.seconds

    def pendingFetches(): Int =
      (PathAware(http, http.path / "localhost_9001") ? HowManyPendingRequests).mapTo[Int].result(3.seconds).fold(f => throw f, s => s)

    pendingFetches() must be(99)

    val secondResponse =  expectMsgType[(RunId, ResourceResponse)](3.seconds)

    pendingFetches() must be(98)

  }


}

