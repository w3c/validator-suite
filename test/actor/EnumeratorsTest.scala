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
import play.api.libs.iteratee.{ Error => _, _ }
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
    val resourceDatas = runningJob.resourceDatas()
    val groupedAssertionDatas = runningJob.groupedAssertionDatas()

    val foo = URL("http://example.com/foo")
    val bar = URL("http://example.com/bar")

    val assertion1 =
      Assertion(
        url = foo,
        assertor = AssertorId("id1"),
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Error,
        description = None)

    val assertion2 =
      Assertion(
        url = foo,
        assertor = AssertorId("id2"),
        contexts = Vector.empty,
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None)

    val assertion3 =
      Assertion(
        url = bar,
        assertor = AssertorId("id2"),
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None)

    val httpResponse = HttpResponse(
      url = foo,
      method = GET,
      status = 200,
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(foo, foo, bar), Some(Doctype("html", "", "")))

    val ar1 = AssertorResult(runId, AssertorId("id1"), foo, Map(foo -> Vector(assertion1)))
    val ar2 = AssertorResult(runId, AssertorId("id2"), foo, Map(foo -> Vector(assertion2)))
    val ar3 = AssertorResult(runId, AssertorId("id3"), foo, Map(bar -> Vector(assertion3)))

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      e1 <- Iteratee.head[RunEvent]
      e2 <- Iteratee.head[RunEvent]
      _ = jobActor ! httpResponse
      _ = jobActor ! ar1
      _ = jobActor ! ar2
      _ = jobActor ! ar3
      e3 <- Iteratee.head[RunEvent]
      e4 <- Iteratee.head[RunEvent]
      e5 <- Iteratee.head[RunEvent]
      e6 <- Iteratee.head[RunEvent]
    } yield Try {
      val Some(CreateRunEvent(_, jobId, _, _, _, _)) = e1
      val Some(ResourceResponseEvent(_, _, _, hr2: HttpResponse, _)) = e2
      val Some(ResourceResponseEvent(_, _, _, hr3: HttpResponse, _)) = e3
      val Some(AssertorResponseEvent(_, _, _, a4: AssertorResult, _)) = e4
      val Some(AssertorResponseEvent(_, _, _, a5: AssertorResult, _)) = e5
      val Some(AssertorResponseEvent(_, _, _, a6: AssertorResult, _)) = e6
      jobId must be(job.id)
      hr2.url must be(URL("http://localhost:9001/"))
      hr3 must be(httpResponse)
      a4 must be(ar1)
      a5 must be(ar2)
      a6 must be(ar3)
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

    val rds = (resourceDatas &> Enumeratee.take(2) |>>> Iteratee.getChunks[ResourceData]).getOrFail()

    val rd1 = rds.find(_.url == foo).get
    rd1.warnings must be(1)
    rd1.errors must be(2)

    val rd2 = rds.find(_.url == bar).get
    rd2.warnings must be(2)
    rd2.errors must be(0)

    val gads = (groupedAssertionDatas &> Enumeratee.take(2) |>>> Iteratee.getChunks[GroupedAssertionData]).getOrFail()

    val gad1 = gads.find(_.assertor == ar1.assertor).get
    gad1.severity must be(Error)
    gad1.occurrences must be(2)
    gad1.resources must be(Vector(foo))

    val gad2 = gads.find(_.assertor == ar2.assertor).get
    gad2.severity must be(Warning)
    gad2.occurrences must be(3)
    gad2.resources.toSet must be(Set(foo, bar))

  }
  
}
