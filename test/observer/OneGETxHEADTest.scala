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
  * 1 GET       10 HEAD
  */
class OneGETxHEADTest extends ObserverTestHelper(new org.w3.vs.Production { }) {
  
  val j = 10
  
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
      unfiltered.jetty.Http(8080).filter(Website((1 to j) map { i => "/" --> ("http://localhost:8081/"+i) }).toPlanify),
      unfiltered.jetty.Http(8081).filter(Website(Seq()).toPlanify)
  )

  "test OneGETxHEAD" in {
    http.authorityManagerFor(URL("http://localhost:8081/")).sleepTime = 0
    val observer = observerCreator.observerOf(run)
    def ris = store.listResourceInfos(run.id).right.get
    def cond = ris.size == 11
    awaitCond(cond, 3 seconds, 50 milliseconds)
    val urls8081 = ris filter { _.url.authority == "localhost:8081" }
    urls8081 must have size(j)
  }

      // should test for the GET and HEAD of course
//    val (links, timestamps) = (responseDAO.getLinksAndTimestamps(actionId) filter { case (url, _) => url.authority == "localhost:8081" }).unzip
//    val a = Response.averageDelay(timestamps)
//    println(a)
//    assert(a >= Crawler.defaultDelay)
//    assert(a < Crawler.defaultDelay + j*15)

}

