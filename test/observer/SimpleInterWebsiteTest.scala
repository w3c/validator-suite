package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {

  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost_9001",
      entrypoint=URL("http://localhost:9001/"),
      distance=1,
      linkCheck=true,
      maxNumberOfResources = 100,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val jobConf = JobConfiguration(strategy = strategy, creator = userTest.id, organization = organizationTest.id, name = "@@")
  
  val servers = Seq(
      unfiltered.jetty.Http(9001).filter(Website(Seq("/" --> "http://localhost:9002/")).toPlanify),
      unfiltered.jetty.Http(9002).filter(Website(Seq()).toPlanify)
  )

  "test simpleInterWebsite" in {
    store.putOrganization(organizationTest)
    store.putJob(jobConf).waitResult()
    val job = Job(jobConf)
    job.refresh()
    job.listen(testActor)
    fishForMessagePF(3.seconds) {
      case UpdateData(jobData) if jobData.activity == Idle => {
        def ris = store.listResourceInfos(jobConf.id).waitResult()
        ris must have size 2
      }
    }
  }
  
}
