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
import play.api.libs.iteratee._

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
    val Running(runId, actorPath) = runningJob.status
    runningJob.id must be(job.id)

    // register to the death of the JobActor
    val jobActorRef = system.actorFor(actorPath)
    watch(jobActorRef)

    // wait for the first ResourceResponseEvent
    (runningJob.enumerator() |>>> waitFor[RunEvent] { case _: ResourceResponseEvent => () }).getOrFail(3.seconds)

    // kill the jobActor
    jobActorRef ! PoisonPill

    // wait for the death notification
    val terminated = fishForMessagePF(3.seconds) { case event: Terminated => event }

    // make sure we've seen the right death
    terminated.actor must be(jobActorRef)

    // then resume!
    val rJob = Job.get(jobId).getOrFail()

    // the database must know that the job was still Running
    rJob.status must be(runningJob.status)

    // now we revive the actor
    val resume = rJob.resume().getOrFail()
    resume must be(())

    val completeRunEvent =
      (rJob.enumerator() |>>> waitFor[RunEvent]{ case e: CompleteRunEvent => e }).getOrFail(3.seconds)

    completeRunEvent.runData.resources must be(circumference + 1)

  }
  
}
