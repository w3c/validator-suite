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

class StopActionTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)
  
  val servers = Seq(Webserver(9001, Website.cyclic(1000).toServlet))

  "test stop" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(20)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    runningJob.listen(testActor)

    fishForMessagePF(3.seconds) {
      case NewResource(_, ri) => ri.url must be(URL("http://localhost:9001/"))
    }

    // note: you can block on that if you wanted
    runningJob.cancel()

    // but here we want to check that the message is sent
    fishForMessagePF(3.seconds) {
      case RunCancelled(_) => {
        val rrs = ResourceResponse.getFor(runId).getOrFail()
        rrs.size must be < (100)
      }
    }
  }
  
}
