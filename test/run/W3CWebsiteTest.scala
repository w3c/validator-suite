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
import java.io.File

abstract class W3CWebsiteTest extends RunTestHelper/*({
try {
  val configuration = new DefaultTestConfiguration {
    override val httpCacheOpt = Some(new Cache(new File("test/resources/w3c-cache")))
  }
  configuration
} catch {
  case e: Exception => e.printStackTrace ; throw e
}
})*/ with TestKitHelper with Inside {

  val strategy =
    Strategy( 
      entrypoint=URL("http://www.w3.org/"),
      linkCheck=false,
      maxResources = 10,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = AssertorsConfiguration.default)
  
  val job = Job.createNewJob(name = "w3c", strategy = strategy, creatorId = userTest.id)

  val servers = Seq.empty

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
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "www.w3.org") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    runEventBus.subscribe(testActor, FromJob(job.id))

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
