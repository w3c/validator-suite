package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.util.akkaext._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.Util._
import play.api.libs.iteratee._
import org.w3.vs.util.TestData
import org.w3.vs._
import play.api.Mode
import org.w3.vs.model.Running
import org.w3.vs.model.DoneRunEvent
import org.w3.vs.util.Webserver
import org.w3.vs.model.Running
import org.w3.vs.model.DoneRunEvent
import org.w3.vs.util.Webserver

class CyclicWebsiteCrawlTest extends VSTest[ActorSystem with Database with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  val circumference = 10

  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  val http = vs.httpActorRef
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  val job = TestData.job

  "test cyclic" in {

//    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.resources must be(circumference + 1)

    // just checking that the data in the store is correct

//    val finalJob = Job.get(job.id).getOrFail()
//
//    finalJob.latestDone must be(Some(finalJob.status))
//
//    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
//      reason must be(Completed)
//    }

  }

}
