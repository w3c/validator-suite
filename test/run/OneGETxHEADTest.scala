package org.w3.vs.run

import org.w3.vs._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.web._
import org.w3.vs.web.Http._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.timer._
import play.api.Mode
import play.api.libs.iteratee._

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
abstract class OneGETxHEADTest extends VSTest with ServersTest with TestData with WipeoutData {

//  implicit val vs = new ValidatorSuite { val mode = Mode.Test }
//
//  val j = 10
//
//  val servers = Seq(
//      Webserver(9001, (Website((1 to j) map { i => "/" --> ("http://localhost:9001/"+i) }).toServlet()))
//  )
//
//  val job = TestData.job  
//
//  "test OneGETxHEAD" in {
//    (for {
//      _ <- User.save(userTest)
//      _ <- Job.save(job)
//    } yield ()).getOrFail()
//    
//    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
//
//    val runningJob = job.run().getOrFail()
//    val Running(runId, actorName) = runningJob.status
//
//    val completeRunEvent =
//      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()
//
//    completeRunEvent.resources should be(j + 1)
//
//  }

}
