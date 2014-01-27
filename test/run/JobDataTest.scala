/*
package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.model._
import org.w3.vs.util.website.Website
import org.w3.vs.util.timer._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee._
import org.w3.vs.model.Running
import org.w3.vs.model.AssertorResponseEvent
import org.w3.vs.util.TestData
import org.w3.vs._
import play.api.Mode
import org.w3.vs.model.Running
import org.w3.vs.util.Webserver
import org.w3.vs.model.AssertorResponseEvent
import org.w3.vs.model.Running
import org.w3.vs.util.Webserver
import org.w3.vs.model.AssertorResponseEvent
import org.w3.vs.model.Running
import org.w3.vs.util.Webserver
import org.w3.vs.model.AssertorResponseEvent
import org.w3.vs.util.akkaext.PathAware
import org.w3.vs.web.Http.SetSleepTime

class JobDataTest extends VSTest[ActorSystem with Database with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  val servers = Seq(Webserver(9001, Website.tree(4).toServlet()))
  val http = vs.httpActorRef
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

  "Job.jobData() should be subscribed to future updates even if the job was Idle" in {

    val job = TestData.job

    // Save the job
    Job.save(job).getOrFail()

    val rdsBeforeRun = job.getResourceDatas().getOrFail()
    rdsBeforeRun should be('empty)

    // Get the enum first
    val enum = job.jobDatas() &> Enumeratee.mapConcat(_.toSeq)

    // Then run the job
    val runningJob: Job = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status
    val jobActor = vs.system.actorFor(actorName.actorPath)

    // generating some stuff
    jobActor ! TestData.ar1
    jobActor ! TestData.ar2
    jobActor ! TestData.ar3

    // make sure that at least one of these was received
    val foo = (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[AssertorResponseEvent]()).getOrFail()

    // We should receive an update even though the enumerator was
    // started *before* starting the run
    (enum |>>> waitFor[JobData]()).getOrFail()

    // just wait for a RunEvent so we now that something interesting had happened
    val rdsAfterRun = runningJob.getResourceDatas().getOrFail()
    rdsAfterRun should not be('empty)

  }

}
*/
