package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

class WebsiteWithRedirectsCrawlTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorSelector = AssertorSelector.noAssertor)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id, assertorConfiguration = AssertorConfiguration.default)

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclicWithRedirects(circumference).toServlet))
  
  "test cyclic" in {
    
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.listen(testActor)
    
    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail(3.seconds)
        // the redirect URLs are not counted
        rrs.filterNot(_.url.toString endsWith ",redirect") must have size (circumference + 1)
      }
    }

  }
  
}
