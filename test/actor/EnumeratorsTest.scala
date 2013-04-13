package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.util.akkaext._
import org.w3.vs.http.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.Util._
import play.api.libs.iteratee.{ Error => _, _ }
import scala.util._
import org.w3.vs._
import org.w3.vs.model.AssertorResult
import org.w3.vs.http.Http.SetSleepTime
import org.w3.vs.model.CreateRunEvent
import org.w3.vs.model.ResourceData
import org.w3.vs.model.AssertorResponseEvent
import org.w3.vs.model.Running
import org.w3.vs.model.ResourceResponseEvent
import org.w3.vs.util.Webserver
import play.api.Mode

class EnumeratorsTest extends VSTest[Database with ActorSystem with HttpClient with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  import TestData._

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test enumerators" in {

    val http = vs.httpActorRef
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(10000)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status
    val jobActor = vs.system.actorFor(actorPath)

    val runEvents = runningJob.runEvents()
    val jobDatas = runningJob.jobDatas()
    val runDatas = runningJob.runDatas()
    val resourceDatas = runningJob.resourceDatas()
    val groupedAssertionDatas = runningJob.groupedAssertionDatas()

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      e1 <- waitFor[CreateRunEvent]()
      e2 <- waitFor[ResourceResponseEvent]()
      _ = jobActor ! httpResponse
      _ = jobActor ! ar1
      _ = jobActor ! ar2
      _ = jobActor ! ar3
      e3 <- waitFor[ResourceResponseEvent]()
      e4 <- waitFor[AssertorResponseEvent]()
      e5 <- waitFor[AssertorResponseEvent]()
      e6 <- waitFor[AssertorResponseEvent]()
    } yield Try {
      val CreateRunEvent(_, jobId, _, _, _, _) = e1
      val ResourceResponseEvent(_, _, `runId`, hr2: HttpResponse, _) = e2
      val ResourceResponseEvent(_, _, `runId`, hr3: HttpResponse, _) = e3
      val AssertorResponseEvent(_, _, `runId`, a4: AssertorResult, _) = e4
      val AssertorResponseEvent(_, _, `runId`, a5: AssertorResult, _) = e5
      val AssertorResponseEvent(_, _, `runId`, a6: AssertorResult, _) = e6
      jobId must be(job.id)
      hr2.url must be(URL("http://localhost:9001/"))
      hr3 must be(httpResponse)
      a4 must be(ar1)
      a5 must be(ar2)
      a6 must be(ar3)
    }

    (runEvents &> Enumeratee.mapConcat(_.toSeq) &> eprint |>>> test()).getOrFail().get

    val jobData = 
      (jobDatas &> Enumeratee.mapConcat(_.toSeq) |>>> Iteratee.head[JobData]).getOrFail().get

    jobData.id must be (job.id)
    jobData.entrypoint must be(strategy.entrypoint)
    jobData.resources must be(2)

    val runData = 
      (runDatas &> Enumeratee.mapConcat(_.toSeq) |>>> Iteratee.head[RunData]).getOrFail().get

    runData.resources must be(2)

    val instantRunData = runningJob.getRunData().getOrFail()
    instantRunData must be(runData)

    val rds = (resourceDatas &> Enumeratee.mapConcat(_.toSeq) &> Enumeratee.take(2) |>>> Iteratee.getChunks[ResourceData]).getOrFail()

    val rd1 = rds.find(_.url == foo).get
    rd1.warnings must be(3) // from assertion2
    rd1.errors must be(2)   // from assertion1

    val rd2 = rds.find(_.url == bar).get
    rd2.warnings must be(2) // from assertion3
    rd2.errors must be(0)   // from assertion3

    val gads = (groupedAssertionDatas &> Enumeratee.mapConcat(_.toSeq) &> Enumeratee.take(2) |>>> Iteratee.getChunks[GroupedAssertionData]).getOrFail()

    val gad1 = gads.find(_.id == assertion1.id).get
    gad1.severity must be(Error)
    gad1.occurrences must be(2)
    gad1.resources must be(Map(foo -> 2))

    val gad2 = gads.find(_.id == commonId).get
    gad2.severity must be(Warning)
    gad2.occurrences must be(5)
    gad2.resources must be(Map(foo -> 3, bar -> 2))

  }
  
}
