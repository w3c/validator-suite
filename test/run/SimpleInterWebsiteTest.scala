package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.vs.util.Util._
import play.api.libs.iteratee._
import org.w3.vs._
import org.w3.vs.util.TestData
import org.w3.vs.model.Running
import org.w3.vs.util.Webserver
import org.w3.vs.model.DoneRunEvent
import play.api.Mode

/**
  * Server 1 -> Server 2
  * 1 GET       1 HEAD
  */
class SimpleInterWebsiteTest extends VSTest[ActorSystem with HttpClient with Database with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  val servers = Seq(
      Webserver(9001, Website(Seq("/" --> "http://localhost:9001/1")).toServlet)
  )

  "test simpleInterWebsite" in {

    val job = TestData.job
    val user = TestData.user

//    (for {
//      _ <- User.save(user)
//      _ <- Job.save(job)
//    } yield ()).getOrFail()

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.resources must be(2)

  }
  
}

