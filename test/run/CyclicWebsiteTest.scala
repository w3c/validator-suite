package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.web.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import play.api.libs.iteratee._
import org.w3.vs.util.TestData
import org.w3.vs._
import play.api.Mode
import org.w3.vs.util.timer._

class CyclicWebsiteCrawlTest extends VSTest with ServersTest with TestData with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val circumference = 10

  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet()))

  val job = TestData.job

  "test cyclic" in {

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources should be(circumference + 1)

    // just checking that the data in the store is correct

//    val finalJob = Job.get(job.id).getOrFail()
//
//    finalJob.latestDone should be(Some(finalJob.status))
//
//    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
//      reason should be(Completed)
//    }

  }

}
