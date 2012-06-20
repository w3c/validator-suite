package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.specs2.mutable.Specification
import akka.dispatch.Await
import akka.util.duration._
import akka.util.Duration
import java.util.concurrent.TimeUnit.SECONDS
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import akka.actor._
import play.api.libs.iteratee._

/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class PendingRunTest extends RunTestHelper(new DefaultProdConfiguration {}) with TestKitHelper {
  
  val strategy =
    Strategy(
      id=StrategyId(), 
      entrypoint=URL("http://localhost:9001/1/"),
      linkCheck=true,
      maxResources = 10,
      filter=Filter.includeEverything).noAssertor()
  
  val job = Job(
    strategy = strategy,
    creatorId = userTest.id,
    organizationId = organizationTest.id,
    name = "@@")
  
  val servers = Seq(Webserver(9001, Website.tree(20).toServlet))

  "test PendingRunTest" in {

    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)

    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    
    job.run()

    job.listen(testActor)
    
    val runId1 = fishForMessagePF(3.seconds) { case NewResource(ri) => ri.runId }

    job.run()

    val (runId2, timestamp) = fishForMessagePF(3.seconds) {
      case NewResource(ri) if ri.runId != runId1 => (ri.runId, ri.timestamp)
    }

    job ! HttpResponse(job.id, runId1, URL("http://localhost:9001/1/3/"), HEAD, 200, Map.empty, "")

    fishForMessagePF(3.seconds) {
      case UpdateData(_, activity) if activity == Idle => ()
    }

    Thread.sleep(100)

    val rrs1 = ResourceResponse.getForRun(runId1).result(1.second) getOrElse sys.error("getForRun")
    val rrs2 = ResourceResponse.getForRun(runId2).result(1.second) getOrElse sys.error("getForRun")

    // there must be a resource in the first run that has arrived *after* at least one resource in the second run
    val condition = rrs1 exists { rr1 => rrs2 exists { rr2 => rr1.timestamp isAfter rr2.timestamp } }

    condition must be (true)

  }

}
