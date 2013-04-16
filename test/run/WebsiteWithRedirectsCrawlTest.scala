package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.util.akkaext._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.Util._
import play.api.libs.iteratee._
import org.w3.vs._
import play.api.Mode

class WebsiteWithRedirectsCrawlTest extends VSTest[ActorSystem with HttpClient with Database with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclicWithRedirects(circumference).toServlet()))
  
  "test cyclic" in {

    val job = TestData.job
    val user = TestData.user

    (for {
      _ <- User.save(user)
      _ <- Job.save(job)
    } yield ()).getOrFail()

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.resources must be(circumference + 1)

  }
  
}
