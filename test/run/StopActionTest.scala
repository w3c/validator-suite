package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.Util._
import play.api.libs.iteratee.{ Done => ItDone, Error => ItError, _ }
import scala.util.Try
import org.w3.vs.util.TestData
import org.w3.vs._
import play.api.Mode
import org.w3.vs.model._

class StopActionTest extends VSTestKit[ActorSystem with HttpClient with Database with RunEvents](
  new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents
) with ServersTest with TestData {

  val servers = Seq(Webserver(9001, Website.cyclic(1000).toServlet))

  val job = TestData.job
  val user = TestData.user

  "test stop" in {

    // Save the job because we'll check the db
    Job.save(job).getOrFail()

//    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(20)

    vs.runEventBus.subscribe(testActor, FromJob(job.id))

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      rr <- waitFor[RunEvent] { case ResourceResponseEvent(_, _, _, rr, _) => rr }
      _ = runningJob.cancel()
      cancelEvent <- waitFor[RunEvent] { case e @ DoneRunEvent(_, _, _, Cancelled, _, _, _, _, _, _) => e }
    } yield Try {
      rr.url must be(URL("http://localhost:9001/"))
      cancelEvent.resources must be < (100)
    }

    (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> test()).getOrFail().get

    // just checking that the data in the store is correct

    val finalJob = Job.get(job.id).getOrFail()

    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
      reason must be(Cancelled)
    }

  }
  
}
