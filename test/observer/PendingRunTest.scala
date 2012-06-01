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


/**
  * Server 1 -> Server 2
  * 1 GET       10 HEAD
  */
class PendingRunTest extends RunTestHelper(
  new DefaultProdConfiguration {
    override val system: ActorSystem = {
      val vs = ActorSystem("vs")
      vs.actorOf(Props(new OrganizationsActor()(this)), "organizations")
      vs.actorOf(Props(new Http()(this)), "http")
      vs
    }
  }) with TestKitHelper {
  
//  val strategy =
//    Strategy(
//      id=StrategyId(), 
//      entrypoint=URL("http://localhost:9001/1/"),
//      linkCheck=true,
//      maxResources = 10,
//      filter=Filter.includeEverything).noAssertor()
//  
//  val job = Job(
//    strategy = strategy,
//    creatorId = play.api.Global.tgambet.id,
//    organizationId = play.api.Global.w3c.id,
//    name = "@@")
  
  val job = play.api.Global.w3
  val org = play.api.Global.w3c
  
  val servers = Seq(unfiltered.jetty.Http(9001).filter(Website.tree(20).toPlanify))

  "test FilteredTreeWebsiteTest" in {
    (for {
      _ <- Organization.save(org)
      _ <- Job.save(job)
    } yield ()).await(5 seconds)
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    job.run()
    //job.listen(testActor)
    
    // there should be a NewResource message sent to the organization actor but no messages are sent to 
    // subscriber actors anymore. I'm not sure how fishForMessage works but it might be irrelevant with
    // the new architecture brought by Concurrent.broadcast. This test could be replaced by something 
    // using job.enumerator
    val runId1 = fishForMessagePF(3.seconds) {
      case NewResource(ri) => ri.runId
    }

    job.run()

    val (runId2, timestamp) = fishForMessagePF(3.seconds) {
      case NewResource(ri) if ri.runId != runId1 => (ri.runId, ri.timestamp)
    }

    job ! HttpResponse(job.id, runId1, URL("http://localhost:9001/1/3/"), HEAD, 200, Map.empty, "")

    /*fishForMessagePF(3.seconds) {
      case UpdateData(jobData) if jobData.activity == Idle => ()
    }*/

    /*store.listResourceInfos(job.id).waitResult().toSeq.sortBy(_.timestamp) foreach { ri =>
      println(ri.toTinyString)
    }*/

    //val risAfterRefresh = store.listResourceInfosByRunId(runId1, after = Some(timestamp)).waitResult()

    //risAfterRefresh must not be ('Empty)

  }

}
