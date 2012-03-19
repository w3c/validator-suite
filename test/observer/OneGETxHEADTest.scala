package org.w3.vs.run

import org.w3.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class OneGETxHEADTest extends RunTestHelper(new DefaultProdConfiguration { }) {
  
  val j = 10
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:9001",
      entrypoint=URL("http://localhost:9001/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val job = JobConfiguration.fake(strategy = strategy)
  
  val servers = Seq(
      unfiltered.jetty.Http(9001).filter(Website((1 to j) map { i => "/" --> ("http://localhost:9002/"+i) }).toPlanify),
      unfiltered.jetty.Http(9002).filter(Website(Seq()).toPlanify)
  )

  "test OneGETxHEAD" in {
    http.authorityManagerFor(URL("http://localhost:9002/")).sleepTime = 0
    val run = jobCreator.runOf(job)
    run.refresh()
    def ris = store.listResourceInfos(job.id) getOrElse sys.error("was not a Success")
    def cond = ris.size == 11
    awaitCond(cond, 3 seconds, 50 milliseconds)
    val urls8081 = ris filter { _.url.authority == "localhost:9002" }
    urls8081 must have size(j)
  }

      // should test for the GET and HEAD of course
//    val (links, timestamps) = (responseDAO.getLinksAndTimestamps(actionId) filter { case (url, _) => url.authority == "localhost:8081" }).unzip
//    val a = Response.averageDelay(timestamps)
//    println(a)
//    assert(a >= Crawler.defaultDelay)
//    assert(a < Crawler.defaultDelay + j*15)

}

