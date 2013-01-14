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

class CyclicWebsiteCrawlTest extends RunTestHelper with TestKitHelper with Inside {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test cyclic" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    vsEvents.subscribe(testActor, FromJob(job.id))

    fishForMessagePF(3.seconds) { case _: RunCompleted => () }

    val rrs = ResourceResponse.getFor(runId).getOrFail(3.seconds)
    rrs must have size (circumference + 1)

    // just checking that the data in the store is correct

    val finalJob = Job.get(job.id).getOrFail()

    finalJob.latestDone must be(Some(finalJob.status))

    inside(finalJob.status ) { case Done(runId, reason, completedOn, jobData) =>
      reason must be(Completed)
    }

  }
  
}
