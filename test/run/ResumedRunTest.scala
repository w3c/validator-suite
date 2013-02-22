package org.w3.vs.run

import org.w3.util._
import org.w3.vs.util._
import org.w3.util.website._
import org.w3.vs.model._
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
  
  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet))
  
  "test cyclic + interruption + resuming job" in {
    
    User.save(userTest).getOrFail()
    Job.save(job).getOrFail()

    val jobId = job.id
    
    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)

    val runningJob = job.run().getOrFail()
    runningJob.id must be(jobId)
    val Running(runId, actorPath) = runningJob.status

    val jobActorRef = system.actorFor(actorPath)

    // listen to the death of the jobActor
    watch(jobActorRef)

    // kill the jobActor
    jobActorRef ! PoisonPill

    // wait for the death notification
    val terminated = expectMsgAnyClassOf(3.seconds, classOf[Terminated])

    terminated.actor must be(jobActorRef)

    // then resume!
    val rJob = Job.get(jobId).getOrFail()

    // the database must know that the job was still Running
    rJob.status must be(runningJob.status)

    // now we revive the actor
    val resume = rJob.resume().getOrFail()
    resume must be(())

    runEventBus.subscribe(testActor, FromJob(job.id))
    
    // that's the same test as in cyclic
    fishForMessagePF(3.seconds) {
      case event: CompleteRunEvent => {
        event.runData.resources must be(circumference + 1)
      }
    }

  }
  
}
