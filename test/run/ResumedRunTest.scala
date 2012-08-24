package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import akka.util.duration._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import akka.actor._

class ResumedRunTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorSelector = AssertorSelector.noAssertor)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id, organization = organizationTest.id)

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test cyclic + interruption + resuming job" in {
    
    (for {
      _ <- Organization.save(organizationTest)
      _ <- Job.save(job)
    } yield ()).result(1.second)
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    job.listen(testActor)

    // subscribe to the death of the actor
    // it's not perfect but it should be enough for now
    val listener = system.actorOf(Props(new Actor {
      def receive = {
        case d: DeadLetter ⇒ testActor ! "got DeadLetter"
      }
    }))
    system.eventStream.subscribe(listener, classOf[DeadLetter])

    val (orgId, jobId, runId) = job.run().getOrFail(1.second)

    job.getSnapshot().getOrFail(3.seconds)

    // kills the jobActor
    job ! PoisonPill

    // wait for the actual death (it's more like discovering the body actually)
    fishForMessagePF(3.seconds) { case "got DeadLetter" => () }

    // then resume!
    job.resume()
    job.listen(testActor)
    
    // that's the same test as in cyclic
    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        val rrs = ResourceResponse.bananaGetFor(orgId, jobId, runId).getOrFail(3.seconds)
        rrs must have size (circumference + 1)
      }
    }

  }
  
}
