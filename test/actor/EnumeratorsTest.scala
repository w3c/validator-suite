package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.web._
import org.w3.vs.web.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.timer._
import org.w3.vs.util.iteratee._
import play.api.libs.iteratee.{ Error => _, _ }
import scala.util._
import org.w3.vs._
import play.api.Mode

class EnumeratorsTest extends VSTest with ServersTest with TestData with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  import TestData._

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet(sleepAfterRequest = 500)))
  
  "test enumerators" in {

    val runningJob = job.run().getOrFail() // will also run all default assertors
    val Running(runId, actorName) = runningJob.status
    import vs.timeout
    val jobActor = vs.system.actorSelection(actorName.actorPath).resolveOne().getOrFail()

    val runEvents = runningJob.runEvents()
    val jobDatas = runningJob.jobDatas()
    val runDatas = runningJob.runDatas()
    val resourceDatas = runningJob.resourceDatas(forever = false)
    val groupedAssertionDatas = runningJob.groupedAssertionDatas(forever = false)

    def test(): Iteratee[RunEvent, Try[Unit]] = for {
      e1 <- waitFor[CreateRunEvent]()
      e2 <- waitFor[ResourceResponseEvent]()
      _ = jobActor ! httpResponse
      _ = jobActor ! ar1
      _ = jobActor ! ar2
      _ = jobActor ! ar3
      e3 <- waitFor[RunEvent]{ case rre: ResourceResponseEvent if rre.rr == httpResponse => rre }
      e4 <- waitFor[RunEvent]{ case are: AssertorResponseEvent if List(ar1, ar2, ar3).contains(are.ar) => are }
      e5 <- waitFor[RunEvent]{ case are: AssertorResponseEvent if List(ar1, ar2, ar3).contains(are.ar) => are }
      e6 <- waitFor[RunEvent]{ case are: AssertorResponseEvent if List(ar1, ar2, ar3).contains(are.ar) => are }
    } yield Try {
      val CreateRunEvent(_, jobId, _, _, _, _) = e1
      val ResourceResponseEvent(_, _, `runId`, hr2: HttpResponse, _) = e2
      val ResourceResponseEvent(_, _, `runId`, hr3: HttpResponse, _) = e3
      val AssertorResponseEvent(_, _, `runId`, a4: AssertorResult, _) = e4
      val AssertorResponseEvent(_, _, `runId`, a5: AssertorResult, _) = e5
      val AssertorResponseEvent(_, _, `runId`, a6: AssertorResult, _) = e6
      jobId should be(job.id)
      hr2.url should be(URL("http://localhost:9001/")) // first resource response event
      hr3 should be(httpResponse)
      Set(a4, a5, a6) should be(Set(ar1, ar2, ar3))
    }

    (runEvents &> Enumeratee.mapConcat(_.toSeq) &> eprint() |>>> test()).getOrFail().get

    val jobData = 
      (jobDatas &> Enumeratee.mapConcat(_.toSeq) |>>> Iteratee.head[JobData]).getOrFail().get

    jobData.id should be (job.id)
    jobData.entrypoint should be(strategy.entrypoint)
    jobData.resources should be >=(2)

    val runData = 
      (runDatas &> Enumeratee.mapConcat(_.toSeq) |>>> Iteratee.head[RunData]).getOrFail().get

    runData.resources should be >= (2)

    val instantRunData = runningJob.getRunData().getOrFail()
    instantRunData.resources should be >= (runData.resources)

    val rds = (resourceDatas &> Enumeratee.mapConcat(_.toSeq) &> Enumeratee.take(circumference) |>>> Iteratee.getChunks[ResourceData]).getOrFail()

    val rd1 = rds.find(_.url == foo).get
    rd1.warnings should be(3) // from assertion2
    rd1.errors should be(2)   // from assertion1

    val rd2 = rds.find(_.url == bar).get
    rd2.warnings should be(2) // from assertion3
    rd2.errors should be(0)   // from assertion3

    // wait for all of them
    val gads = (groupedAssertionDatas &> Enumeratee.mapConcat(_.toSeq) |>>> Iteratee.getChunks[GroupedAssertionData]).getOrFail()

    val gad1 = gads.find(_.id == assertion1.id).get
    gad1.severity should be(Error)
    gad1.occurrences should be(2)
    gad1.resources should be(Map(foo -> 2))

    val gad2 = gads.find(_.id == commonId).get
    gad2.severity should be(Warning)
    gad2.occurrences should be(5)
    gad2.resources should be(Map(foo -> 3, bar -> 2))

  }
  
}
