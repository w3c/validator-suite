package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class FilteredTreeWebsiteTest extends RunTestHelper with TestKitHelper {
  
  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/1/"),
      linkCheck=true,
      maxResources = 50,
      filter=Filter.includePrefixes("http://localhost:9001/1", "http://localhost:9001/3"),
      assertorsConfiguration = Map.empty)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)

  val servers = Seq(Webserver(9001, Website.tree(4).toServlet))

  "test FilteredTreeWebsiteTest" in {

    (for {
      a <- Organization.save(organizationTest)
      b <- Job.save(job)
    } yield ()).result(1.second)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail(3.seconds)
        rrs must have size (50)
        rrs foreach { rr =>
          rr.url.toString must startWith regex ("http://localhost:9001/[13]")
        }
      }
    }

  }

}
