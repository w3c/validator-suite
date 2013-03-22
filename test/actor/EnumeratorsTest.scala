package org.w3.vs.assertor

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
import play.api.libs.iteratee._
import scala.util._
import org.w3.util.html.Doctype

class EnumeratorsTest extends RunTestHelper with TestKitHelper with Inside {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test enumerators" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(10000)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status
    val jobActor = system.actorFor(actorPath)

    val runEvents = runningJob.runEvents()
    val jobDatas = runningJob.jobDatas()
    val runDatas = runningJob.runDatas()

    val httpResponse = HttpResponse(
      url = URL("http://example.com/foo"),
      method = GET,
      status = 200,
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")), Some(Doctype("html", "", "")))

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      e1 <- Iteratee.head[RunEvent]
      e2 <- Iteratee.head[RunEvent]
      _ = jobActor ! httpResponse
      e3 <- Iteratee.head[RunEvent]
    } yield Try {
      val Some(CreateRunEvent(_, jobId, _, _, _, _)) = e1
      val Some(ResourceResponseEvent(_, _, _, hr2: HttpResponse, _)) = e2
      val Some(ResourceResponseEvent(_, _, _, hr3: HttpResponse, _)) = e3
      jobId must be(job.id)
      hr2.url must be(URL("http://localhost:9001/"))
      hr3.url must be(URL("http://example.com/foo"))
    }

    (runEvents |>>> test()).getOrFail().get

    val jobData = 
      (jobDatas |>>> Iteratee.head[JobData]).getOrFail().get

    jobData.id must be (job.id)
    jobData.entrypoint must be(strategy.entrypoint)
    jobData.resources must be(2)

    val runData = 
      (runDatas |>>> Iteratee.head[RunData]).getOrFail().get

    runData.resources must be(2)

  }
  
}
