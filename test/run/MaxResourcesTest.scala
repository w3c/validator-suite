package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.util.akkaext._
import org.w3.vs.http.Http._
import org.w3.vs.util.Util._
import play.api.libs.iteratee._
import org.w3.vs.util.TestData
import org.w3.vs._
import org.w3.vs.model.Running
import org.w3.vs.model.DoneRunEvent
import org.w3.vs.http.Http.SetSleepTime
import org.w3.vs.util.Webserver
import play.api.Mode

class MaxResourcesTest extends VSTest[ActorSystem with HttpClient with Database with RunEvents] with ServersTest with TestData {

  implicit val vs = new ValidatorSuite(mode =  Mode.Test)
    with DefaultActorSystem
    with DefaultDatabase
    with DefaultHttpClient
    with DefaultRunEvents

  val servers = Seq(Webserver(9001, Website.tree(4).toServlet()))

  val job = TestData.job

  val maxResources = TestData.job.strategy.maxResources

  s"""shoudldn't access more that $maxResources resources""" in {

    val http = vs.httpActorRef
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources must be(maxResources)

  }

}
