package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
import org.w3.vs.actor.message._
import org.w3.util.akkaext._
import org.w3.vs.http._
import org.w3.vs.http.Http._
import akka.actor._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.w3.util.Util._

class ResumedRunTest extends RunTestHelper with TestKitHelper {

  val strategy =
    Strategy( 
      entrypoint=URL("http://localhost:9001/"),
      linkCheck=true,
      maxResources = 100,
      filter=Filter(include=Everything, exclude=Nothing),
      assertorsConfiguration = Map.empty)
  
  val job = Job(name = "@@", strategy = strategy, creator = userTest.id)

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test cyclic + interruption + resuming job" in {
    
    (for {
      _ <- User.save(userTest)
      _ <- Job.save(job)
    } yield ()).getOrFail()
    
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

    val (userId, jobId, runId) = job.run().getOrFail()

    job.waitLastWrite().getOrFail()

    // kills the jobActor
    job ! PoisonPill

    // wait for the actual death (it's more like discovering the body actually)
    fishForMessagePF(3.seconds) { case "got DeadLetter" => () }

    // then resume!
    job.listen(testActor)
    job.resume()
    
    // that's the same test as in cyclic
    fishForMessagePF(3.seconds) {
      case UpdateData(_, _, activity) if activity == Idle => {
        job.waitLastWrite().getOrFail()
        val rrs = ResourceResponse.getFor(runId).getOrFail()
        rrs must have size (circumference + 1)
      }
    }

  }
  
}
