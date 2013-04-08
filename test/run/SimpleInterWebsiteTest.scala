package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.Util._
import play.api.libs.iteratee._

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val servers = Seq(
      Webserver(9001, Website(Seq("/" --> "http://localhost:9001/1")).toServlet)
  )

  "test simpleInterWebsite" in {
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.resources must be(2)

  }
  
}
