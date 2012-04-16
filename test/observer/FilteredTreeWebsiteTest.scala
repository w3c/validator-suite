package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class FilteredTreeWebsiteTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {
  
  val strategy =
    Strategy(
      uuid=java.util.UUID.randomUUID(), 
      name="localhost_9001",
      entrypoint=URL("http://localhost:9001/1/"),
      distance=4,
      linkCheck=true,
      maxNumberOfResources = 50,
      filter=Filter.includePrefixes("http://localhost:9001/1", "http://localhost:9001/3")).noAssertor()
  
  val job = Job(strategy = strategy, creator = userTest.id, organizationId = organizationTest.id, name = "@@")
  
  val servers = Seq(unfiltered.jetty.Http(9001).filter(Website.tree(4).toPlanify))

  "test FilteredTreeWebsiteTest" in {
    store.putOrganization(organizationTest)
    store.putJob(job).waitResult()
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    job.refresh()
    job.listen(testActor)
    fishForMessagePF(3.seconds) {
      case UpdateData(jobData) if jobData.activity == Idle => {
        def ris = store.listResourceInfos(job.id).waitResult()
        ris must have size (50)
        ris foreach { ri =>
          ri.url.toString must startWith regex ("http://localhost:9001/[13]")
        }
      }
    }
  }

}
