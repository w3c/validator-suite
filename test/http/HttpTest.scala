package org.w3.vs.http

import org.w3.vs._
import org.scalatest._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util._
import org.w3.util.akkaext._
import Http._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.util.Util._

// the test would be better without extending RunTestHelper...
class HttpTest extends RunTestHelper with Inside {
  
  val servers = Seq(Webserver(9001, Website.cyclic(10).toServlet))
  
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  "testing HEAD on existing URL" in {

    http ! Fetch(URL("http://localhost:9001/"), HEAD)

    val fetchResponse = expectMsgType[ResourceResponse](3.seconds)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/"))
      response.method must be(HEAD)
      response.status must be(200)
    }

  }



  "testing GET on existing URL" in {

    http ! Fetch(URL("http://localhost:9001/"), GET)

    val fetchResponse = expectMsgType[ResourceResponse](3.seconds)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/"))
      response.method must be(GET)
      response.status must be(200)
    }

  }


  "testing HEAD on non-existing URL (404)" in {

    http ! Fetch(URL("http://localhost:9001/404/foo"), HEAD)

    val fetchResponse = expectMsgType[ResourceResponse](3.seconds)

    inside (fetchResponse) { case response: HttpResponse =>
      response.url must be(URL("http://localhost:9001/404/foo"))
      response.method must be(HEAD)
      response.status must be(404)
    }

  }

  
  "testing HEAD on non-existing domain example.com must fail" in {

    http ! Fetch(URL("http://example.co/bar"), HEAD)

    val fetchResponse = expectMsgType[ResourceResponse](3.seconds)

    inside (fetchResponse) { case response: ErrorResponse =>
      response.url must be(URL("http://example.co/bar"))
      response.method must be(HEAD)
    }

  }

  "testing delays" in {

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(200)

    for(i <- 1 to 100) {
      http ! Fetch(URL("http://localhost:9001/"+i), HEAD)
    }

    val fetchResponse = expectMsgType[ResourceResponse](3.seconds)

    implicit val timeout = akka.util.Timeout(3, java.util.concurrent.TimeUnit.SECONDS)

    def pendingFetches(): Int =
      (PathAware(http, http.path / "localhost_9001") ? HowManyPendingRequests).mapTo[Int].getOrFail()

    pendingFetches() must be(99)

    val secondResponse =  expectMsgType[ResourceResponse](3.seconds)

    pendingFetches() must be(98)

  }

}

