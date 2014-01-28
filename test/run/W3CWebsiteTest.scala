package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.web._
import org.w3.vs.web.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.timer._
import org.scalatest.Inside
import java.io.File
import org.w3.vs._
import play.api.Mode

abstract class W3CWebsiteTest extends VSTestKit with TestData with WipeoutData with Inside {

  val servers = Seq.empty

  val job = TestData.job
  val user = TestData.user

  "test w3c website" in {
    
    (for {
      _ <- User.save(user)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
//    PathAware(http, http.path / "www.w3.org") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    vs.runEventBus.subscribe(testActor, FromJob(job.id))

    fishForMessagePF(Duration("60s")) { case _: DoneRunEvent => () }

    val rrs = ResourceResponse.getFor(runId).getOrFail()
    rrs should have size (10)

    // just checking that the data in the store is correct

    val finalJob = Job.get(job.id).getOrFail()

    finalJob.latestDone should be(Some(finalJob.status))

    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
      reason should be(Completed)
    }

    val assertions = finalJob.getAssertions().getOrFail()
    // check that there is no duplicated assertions here
    // how?

  }
  
}
