package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.DefaultProdConfiguration
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._

/**
  * I'm starting to think that the current idea of "run" is silly
  * this test used to test that a ResourceResponse for a previous was actually recorded
  * while it had no impact on the currnet one.
  * well, as we don't do anything with this information anyway, let's forget about it...
  */
//class PendingRunTest extends RunTestHelper(new DefaultProdConfiguration {}) with TestKitHelper {
//  
//  val strategy =
//    Strategy(
//      entrypoint=URL("http://localhost:9001/1/"),
//      linkCheck=true,
//      maxResources = 10,
//      filter=Filter.includeEverything).noAssertor()
//  
//  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)
//  
//  val servers = Seq(Webserver(9001, Website.tree(20).toServlet))
//
//  "test PendingRunTest" in {
//
//    (for {
//      _ <- Organization.save(organizationTest)
//      _ <- Job.save(job)
//    } yield ()).result(1.second)
//
//    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
//
//    job.listen(testActor)
//    
//    val runId1 = job.run().result(3.seconds).toOption.get
//
//    // wait for at least one message to be processed in first run
//    fishForMessagePF(3.seconds) {
//      case NewResource((_, _, runId), _) if runId == runId1 => ()
//    }
//
//    val runId2 = job.run().result(3.seconds).toOption.get
//
//    // wait for at least one message to be processed in second run
//    fishForMessagePF(3.seconds) {
//      case NewResource((_, _, runId), _) if runId == runId2 => ()
//    }
//
//    // even though run1 is terminated, it should be stored in the database
//    job ! HttpResponse(job.id, runId1, URL("http://localhost:9001/1/3/"), HEAD, 200, Map.empty, "")
//
//    fishForMessagePF(3.seconds) {
//      case UpdateData(_, activity) if activity == Idle => ()
//    }
//
//    val rrs1 = ResourceResponse.bananaGetFor(organizationTest.id, job.id, runId1).await(3.seconds).toOption.get
//    val rrs2 = ResourceResponse.bananaGetFor(organizationTest.id, job.id, runId2).await(3.seconds).toOption.get
//
//    // there must be a resource in the first run that has arrived *after* at least one resource in the second run
//    val condition = rrs1 exists { rr1 => rrs2 exists { rr2 => rr1.timestamp isAfter rr2.timestamp } }
//
//    condition must be (true)
//
//  }
//
//}
