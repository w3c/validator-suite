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
object OneGETxHEADTest extends Specification {
  
  val delay = 0
  
  val j = 10
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:8080",
      entrypoint=URL("http://localhost:8080/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))

  val servers = Seq(
      unfiltered.jetty.Http(8080).filter(Website((1 to j) map { i => "/" --> ("http://localhost:8081/"+i) }).toPlanify),
      unfiltered.jetty.Http(8081).filter(Website(Seq()).toPlanify)
  )

  "test OneGETxHEAD" in new ObserverScope(servers)(new org.w3.vs.Production { }) {
    http.authorityManagerFor(URL("http://localhost:8081/")).sleepTime = delay
    val observer = observerCreator.observerOf(ObserverId(), strategy, timeout = Duration(1, SECONDS))
    observer.startExplorationPhase()
    val urls = Await.result(observer.URLs(), Duration(1, SECONDS))
    val urls8081 = urls filter { _.authority == "localhost:8081" }
    urls8081 must have size(j)
  }

      // should test for the GET and HEAD of course
//    val (links, timestamps) = (responseDAO.getLinksAndTimestamps(actionId) filter { case (url, _) => url.authority == "localhost:8081" }).unzip
//    val a = Response.averageDelay(timestamps)
//    println(a)
//    assert(a >= Crawler.defaultDelay)
//    assert(a < Crawler.defaultDelay + j*15)

}

