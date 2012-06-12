package org.w3.vs.http

import org.w3.vs._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import akka.testkit.{TestKit, ImplicitSender}
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util._
import akka.util.Duration
import akka.util.duration._
import akka.dispatch._
import org.w3.util.akkaext._

class HttpTest() extends RunTestHelper(new DefaultProdConfiguration { }) with Inside {
  
  val servers = Seq(Webserver(9001, Website.cyclic(10).toServlet))
  
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  "testing HEAD on existing URL" in {

    val newRunId = RunId()
    val newJobId = JobId()

    http ! Fetch(URL("http://localhost:9001/"), HEAD, newRunId, newJobId)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse => //OkResponse(url, action, status, headers, body, runId) =>
      response.url must be === (URL("http://localhost:9001/"))
      response.action must be === (HEAD)
      response.status must be === 200
      //body must be === ""
      response.runId must be === newRunId
    }

  }



  "testing GET on existing URL" in {

    val newRunId = RunId()
    val newJobId = JobId()
    
    http ! Fetch(URL("http://localhost:9001/"), GET, newRunId, newJobId)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse => //OkResponse(url, action, status, headers, body, runId) =>
      response.url must be === (URL("http://localhost:9001/"))
      response.action must be === (GET)
      response.status must be === 200
      //body must not be ('empty)
      response.runId must be === newRunId
    }

  }


  "testing HEAD on non-existing URL (404)" in {

    val newRunId = RunId()
    val newJobId = JobId()

    http ! Fetch(URL("http://localhost:9001/404/foo"), HEAD, newRunId, newJobId)

    val fetchResponse = expectMsgType[ResourceResponse](1.second)

    inside (fetchResponse) { case response: HttpResponse => //OkResponse(url, action, status, headers, body, runId) =>
      response.url must be === (URL("http://localhost:9001/404/foo"))
      response.action must be === (HEAD)
      response.status must be === 404
      response.runId must be === newRunId
    }

  }

  
  if (System.getProperty("os.name") startsWith "Linux") {

    "testing HEAD on non-existing domain (foo.localhost)" in {
      
      val newRunId = RunId()
      val newJobId = JobId()
      
      http ! Fetch(URL("http://foo.localhost/bar"), HEAD, newRunId, newJobId)
      
      val fetchResponse = expectMsgType[ResourceResponse](1.second)
      
      inside (fetchResponse) { case response: ErrorResponse =>
        response.url must be === (URL("http://foo.localhost/bar"))
        response.action must be === (HEAD)
        response.runId must be === newRunId
      }

    }
  }

  "testing delays" in {

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(200)

    val newRunId = RunId()
    val newJobId = JobId()

    for(i <- 1 to 100) {
      http ! Fetch(URL("http://localhost:9001/"+i), HEAD, newRunId, newJobId)
    }

    val fetchResponse = expectMsgType[ResourceResponse](1.second)
    
    implicit val timeout: akka.util.Timeout = 1.second

    def pendingFetches(): Int =
      (PathAware(http, http.path / "localhost_9001") ? HowManyPendingRequests).mapTo[Int].result(1.second).fold(f => throw f, s => s)

    pendingFetches() must be === (99)

    val secondResponse =  expectMsgType[ResourceResponse](1.second)

    pendingFetches() must be === (98)

  }


}

