package org.w3.vs.observer

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import org.specs2.mutable.Specification

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
object SimpleInterWebsiteTest extends Specification {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:8080",
      entrypoint=URL("http://localhost:8080/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val servers = Seq(
      unfiltered.jetty.Http(8080).filter(Website(Seq("/" --> "http://localhost:8081/")).toPlanify),
      unfiltered.jetty.Http(8081).filter(Website(Seq()).toPlanify)
  )

  "test simpleInterWebsite" in new ObserverScope(servers) {
    val observer = newObserver(strategy, timeout = Duration(1, SECONDS))
    observer.startExplorationPhase()
    val urls = observer.URLs().get
    assert(urls.size === 2)
  }

//    val (links, timestamps) = responseDAO.getLinksAndTimestamps(actionId) .unzip
//    val a = Response.averageDelay(timestamps)
//    assert(a < 20)
  
}
