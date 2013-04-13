package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.Util._
import org.scalatest.Inside
import java.io.File
import org.w3.vs.util.TestData
import org.w3.vs._
import org.w3.vs.model.Running
import org.w3.vs.model.Done
import org.w3.vs.model.DoneRunEvent
import scala.Some
import org.w3.vs.model.FromJob
import play.api.Mode
import org.w3.vs.model.Running
import org.w3.vs.model.Done
import org.w3.vs.model.DoneRunEvent
import scala.Some
import org.w3.vs.model.FromJob
import org.w3.vs.model.Running
import org.w3.vs.model.Done
import org.w3.vs.model.DoneRunEvent
import scala.Some
import org.w3.vs.model.FromJob
import org.w3.vs.model.Running
import org.w3.vs.model.Done
import org.w3.vs.model.DoneRunEvent
import scala.Some
import org.w3.vs.model.FromJob
import org.w3.vs.model.Running
import org.w3.vs.model.Done
import org.w3.vs.model.DoneRunEvent
import scala.Some
import org.w3.vs.model.FromJob

abstract class W3CWebsiteTest extends VSTestKit[ActorSystem with HttpClient with Database with RunEvents](
  new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents
) with TestData with Inside {

/*({
try {
  val configuration = new DefaultTestConfiguration {
    override val httpCacheOpt = Some(new Cache(new File("test/resources/w3c-cache")))
  }
  configuration
} catch {
  case e: Exception => e.printStackTrace ; throw e
}
})*/

  val servers = Seq.empty

  val job = TestData.job
  val user = TestData.user

  override def beforeAll: Unit = {
    org.w3.vs.assertor.LocalValidators.start()
    super.beforeAll()
  }

  override def afterAll: Unit = {
    org.w3.vs.assertor.LocalValidators.stop()
    super.afterAll()
  }

  "test w3c website" in {
    
    (for {
      _ <- User.save(user)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
//    PathAware(http, http.path / "www.w3.org") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    vs.runEventBus.subscribe(testActor, FromJob(job.id))

    fishForMessagePF(10.seconds) { case _: DoneRunEvent => () }

    val rrs = ResourceResponse.getFor(runId).getOrFail()
    rrs must have size (10)

    // just checking that the data in the store is correct

    val finalJob = Job.get(job.id).getOrFail()

    finalJob.latestDone must be(Some(finalJob.status))

    inside(finalJob.status ) { case Done(runId, reason, completedOn, runData) =>
      reason must be(Completed)
    }

    val assertions = finalJob.getAssertions().getOrFail()
    // check that there is no duplicated assertions here
    // how?

  }
  
}
