package org.w3.vs.observer

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends ObserverTestHelper(new org.w3.vs.Production { }) {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:8080",
      entrypoint=URL("http://localhost:8080/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val run = Run(job = Job(strategy = strategy))
  
  val servers = Seq(
      unfiltered.jetty.Http(8080).filter(Website(Seq("/" --> "http://localhost:8081/")).toPlanify),
      unfiltered.jetty.Http(8081).filter(Website(Seq()).toPlanify)
  )

  "test simpleInterWebsite" in {
    val observer = observerCreator.observerOf(run)
    def ris = store.listResourceInfos(run.id).right.get
    def cond = ris.size == 2
    awaitCond(cond, 3 seconds, 50 milliseconds)
  }

//    val (links, timestamps) = responseDAO.getLinksAndTimestamps(actionId) .unzip
//    val a = Response.averageDelay(timestamps)
//    assert(a < 20)
  
}
