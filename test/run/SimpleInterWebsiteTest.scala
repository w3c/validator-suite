package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.timer._
import play.api.libs.iteratee._
import org.w3.vs._
import play.api.Mode

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends VSTest with ServersTest with TestData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val servers = Seq(
      Webserver(9001, Website(Seq("/" --> "http://localhost:9001/1")).toServlet())
  )

  "test simpleInterWebsite" in {

    val job = TestData.job
    val user = TestData.user

//    (for {
//      _ <- User.save(user)
//      _ <- Job.save(job)
//    } yield ()).getOrFail()

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources should be(2)

  }
  
}

