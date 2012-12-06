package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._

class WebsiteWithRedirectsCrawlTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id)

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclicWithRedirects(circumference).toServlet))
  
  "test cyclic" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val (userId, jobId, runId) = job.run().getOrFail()

    job.listen(testActor)
    
    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        job.waitLastWrite().getOrFail()
        val rrs = ResourceResponse.getFor(runId).getOrFail()
        // the redirect URLs are not counted
        rrs.filterNot(_.url.toString endsWith ",redirect") must have size (circumference + 1)
      }
    }

  }
  
}
