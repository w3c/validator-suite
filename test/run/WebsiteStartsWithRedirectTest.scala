/**
 * See #291: https://github.com/w3c/validator-suite/issues/291
 */

/*
package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.timer._
import play.api.libs.iteratee._
import org.w3.vs._
import play.api.Mode
import org.w3.vs.web.URL

class WebsiteStartsWithRedirectTest extends VSTest with ServersTest with TestData with WipeoutData {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val circumference = 10
  
  val servers = Seq(Webserver(9001, Website.cyclicWithRedirects(circumference).toServlet()))
  
  "test cyclic" in {

    val strategy = Strategy(entrypoint = URL("http://localhost:9001/,redirect"), maxResources = 100)

    val user = User.create(email = "", name = "", password = "", isSubscriber = true, credits = 10000)
    val job = Job(name = "@@", strategy = strategy, creatorId = Some(user.id))

    (for {
      _ <- User.save(user)
      _ <- Job.save(job)
    } yield ()).getOrFail()

    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status

    val completeRunEvent =
      (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources should be(circumference + 1)

  }
  
}
*/
