package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

class StopActionTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorSelector = AssertorSelector.noAssertor)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)
  
  val servers = Seq(Webserver(9001, Website.cyclic(1000).toServlet))

  "test stop" in {
    
    (for {
      a <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).await(1.second)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(20)

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case NewResource(_, ri) => ri.url must be(URL("http://localhost:9001/"))
    }

    job.cancel()

    fishForMessagePF(3.seconds) {
      case UpdateData(jobData, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail(3.seconds)
        rrs.size must be < (100)
      }
    }
  }
  
}
