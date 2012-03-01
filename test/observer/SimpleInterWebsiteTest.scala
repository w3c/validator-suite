package org.w3.vs.run

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.prod.Configuration

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends RunTestHelper(new Configuration { }) {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:9001",
      entrypoint=URL("http://localhost:9001/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val job = Job(strategy = strategy)
  
  val servers = Seq(
      unfiltered.jetty.Http(9001).filter(Website(Seq("/" --> "http://localhost:9002/")).toPlanify),
      unfiltered.jetty.Http(9002).filter(Website(Seq()).toPlanify)
  )

  "test simpleInterWebsite" in {
    val run = runCreator.runOf(job)
    def ris = store.listResourceInfos(job.id) getOrElse sys.error("was not a Success")
    def cond = ris.size == 2
    awaitCond(cond, 3 seconds, 50 milliseconds)
  }

//    val (links, timestamps) = responseDAO.getLinksAndTimestamps(actionId) .unzip
//    val a = Response.averageDelay(timestamps)
//    assert(a < 20)
  
}
