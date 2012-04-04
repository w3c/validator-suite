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

class HttpTest() extends RunTestHelper(new DefaultProdConfiguration { }) with Inside {
  
  val servers = Seq(unfiltered.jetty.Http(9001).filter(Website.cyclic(10).toPlanify))
  
  "testing HEAD on existing URL" in {

    val newRunId = RunId.newId()

    http ! Fetch(URL("http://localhost:9001/"), HEAD, newRunId)

    val fetchResponse = expectMsgType[FetchResponse](1.second)

    inside (fetchResponse) { case OkResponse(url, action, status, headers, body, runId) =>
      url must be === (URL("http://localhost:9001/"))
      action must be === (HEAD)
      status must be === 200
      body must be === ""
      runId must be === newRunId
    }

  }



  "testing GET on existing URL" in {

    val newRunId = RunId.newId()

    http ! Fetch(URL("http://localhost:9001/"), GET, newRunId)

    val fetchResponse = expectMsgType[FetchResponse](1.second)

    inside (fetchResponse) { case OkResponse(url, action, status, headers, body, runId) =>
      url must be === (URL("http://localhost:9001/"))
      action must be === (GET)
      status must be === 200
      body must not be ('empty)
      runId must be === newRunId
    }

  }


  "testing HEAD on non-existing URL (404)" in {

    val newRunId = RunId.newId()

    http ! Fetch(URL("http://localhost:9001/404/foo"), HEAD, newRunId)

    val fetchResponse = expectMsgType[FetchResponse](1.second)

    inside (fetchResponse) { case OkResponse(url, action, status, headers, body, runId) =>
      url must be === (URL("http://localhost:9001/404/foo"))
      action must be === (HEAD)
      status must be === 404
      runId must be === newRunId
    }

  }


  "testing HEAD on non-existing domain (foo.localhost)" in {

    val newRunId = RunId.newId()

    http ! Fetch(URL("http://foo.localhost/bar"), HEAD, newRunId)

    val fetchResponse = expectMsgType[FetchResponse](1.second)

    inside (fetchResponse) { case KoResponse(url, action, why, runId) =>
      url must be === (URL("http://foo.localhost/bar"))
      action must be === (HEAD)
      why.getClass.getName must be === ("java.net.ConnectException")
      runId must be === newRunId
    }

  }


}
