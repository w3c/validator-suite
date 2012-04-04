package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.Duration
import java.util.concurrent.TimeUnit._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import akka.testkit.TestKit
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._

class CyclicWebsiteCrawlTest extends RunTestHelper(new DefaultProdConfiguration { }) {
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost:9001",
      entrypoint=URL("http://localhost:9001/"),
      distance=11,
      linkCheck=true,
      maxNumberOfResources = 100,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val jobConf = JobConfiguration(strategy = strategy, creator = userTest.id, organization = organizationTest.id, name = "@@")
  
  val servers = Seq(unfiltered.jetty.Http(9001).filter(Website.cyclic(10).toPlanify))
  
  "test cyclic(10)" in {
    store.putOrganization(organizationTest)
    store.putJob(jobConf)
    Thread.sleep(200)
    //http.authorityManagerFor(URL("http://localhost:9001/")).sleepTime = 0
    val job = Job(jobConf)
    job.refresh()
    def cond = store.listResourceInfos(jobConf.id).waitResult.size == 11
    awaitCond(cond, 3 seconds, 50 milliseconds)
  }
  
//    val (links, timestamps) = responseDAO.getLinksAndTimestamps(actionId) .unzip
//    val a = Response.averageDelay(timestamps)
//    assert(a >= 200 && a < 250)
  
}
