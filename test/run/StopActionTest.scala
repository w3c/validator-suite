package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.web._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.timer._
import play.api.libs.iteratee.{ Done => ItDone, Error => ItError, _ }
import scala.util.Try
import org.w3.vs.util.TestData
import org.w3.vs._
import play.api.Mode

class StopActionTest extends VSTestKit with ServersTest with TestData with WipeoutData {

  val servers = Seq(Webserver(9001, Website.cyclic(1000).toServlet()))

  val job = TestData.job
  val user = TestData.user

  "test stop" in {

    // Save the job because we'll check the db
    Job.save(job).getOrFail()

    vs.runEventBus.subscribe(testActor, FromJob(job.id))

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      rr <- waitFor[RunEvent] { case ResourceResponseEvent(_, _, _, rr, _) => rr }
      _ = runningJob.cancel()
      cancelEvent <- waitFor[RunEvent] { case e @ DoneRunEvent(_, _, _, Cancelled, _, _, _, _, _, _) => e }
    } yield Try {
      rr.url should be(URL("http://localhost:9001/"))
      cancelEvent.resources should be < (100)
    }

    (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> test()).getOrFail().get

  }
  
}
