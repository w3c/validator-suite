package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._
import org.scalatest.Inside
import play.api.libs.iteratee.{ Done => ItDone, Error => ItError, _ }
import scala.util.Try

class StopActionTest extends RunTestHelper with TestKitHelper with Inside {

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

    runEventBus.subscribe(testActor, FromJob(job.id))

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      rr <- waitFor[RunEvent] { case ResourceResponseEvent(_, _, _, rr, _) => rr }
      _ = runningJob.cancel()
      cancelEvent <- waitFor[RunEvent] { case event: CancelRunEvent => event }
    } yield Try {
      rr.url must be(URL("http://localhost:9001/"))
      cancelEvent.runData.resources must be < (100)
    }

    (runningJob.runEvents() |>>> test()).getOrFail().get

    // just checking that the data in the store is correct

//    val finalJob = Job.get(job.id).getOrFail()
//
//    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
//      reason must be(Cancelled)
//    }


  }
  
}
