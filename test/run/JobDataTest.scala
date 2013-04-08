package org.w3.vs.run

import org.w3.vs.util.{TestKitHelper, RunTestHelper}
import org.w3.vs.model._
import org.w3.util.URL
import org.w3.util.website.Website
import org.w3.util.Util._
import org.w3.util.website.Webserver
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee._

class JobDataTest extends RunTestHelper with TestKitHelper {

  import org.w3.vs.assertor.DataTest._

  val servers = Seq(Webserver(9001, Website.tree(4).toServlet))

  "Job.jobData() must be subscribed to future updates even if the job was Idle" in {

    val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail(5.seconds)

    val rdsBeforeRun = job.getResourceDatas().getOrFail()
    rdsBeforeRun must be('empty)

    // Get the enum first
    val enum = job.jobDatas() &> Enumeratee.mapConcat(_.toSeq)

    // Then run the job
    val runningJob: Job = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status
    val jobActor = system.actorFor(actorPath)

    // generating some stuff
    jobActor ! ar1
    jobActor ! ar2
    jobActor ! ar3

    // make sure that at least one of these was received
    val foo = (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[AssertorResponseEvent]()).getOrFail()

    // We should receive an update even though the enumerator was
    // started *before* starting the run
    (enum |>>> waitFor[JobData]()).getOrFail()

    // just wait for a RunEvent so we now that something interesting had happened
    val rdsAfterRun = runningJob.getResourceDatas().getOrFail()
    rdsAfterRun must not be('empty)

  }

}
