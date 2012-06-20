package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

class MaxResourcesTest extends RunTestHelper(new DefaultProdConfiguration { }) with TestKitHelper {

  val maxResources = 100

  val strategy =
    Strategy(
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = maxResources,
      filter=Filter(include=Everything, exclude=Nothing)).noAssertor()
  
  val job = Job(strategy = strategy, creatorId = userTest.id, organizationId = organizationTest.id, name = "@@")
  
  val servers = Seq(Webserver(9001, Website.tree(4).toServlet))

  "shoudldn't access more that 100 resources" in {

    (for {
      a <- Organization.save(organizationTest)
      b <- Job.save(job)
    } yield ()).await(5.second)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    job.run()

    job.listen(testActor)

    fishForMessagePF(3.seconds) {
      case UpdateData(_, activity) if activity == Idle => {
        Thread.sleep(100)
        val run = job.getRun().result(1.second) getOrElse sys.error("getRun")
        val rrs = ResourceResponse.getForRun(run.id).result(1.second) getOrElse sys.error("getForRun")
        rrs must have size (maxResources)
      }
    }

  }
  
}
