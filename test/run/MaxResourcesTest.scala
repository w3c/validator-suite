package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.model._
import org.w3.vs.web.Http._
import org.w3.vs.util.timer._
import play.api.libs.iteratee._
import org.w3.vs._
import play.api.Mode
import scala.concurrent.ExecutionContext.Implicits.global

class MaxResourcesTest extends VSTest with ServersTest with TestData with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val servers = Seq(Webserver(9001, Website.tree(4).toServlet()))

  val job = TestData.job

  val maxResources = TestData.job.strategy.maxResources

  s"""shoudldn't access more that $maxResources resources""" in {

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources should be(maxResources)

  }

}
