package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.iteratee._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.timer._
import scala.concurrent.duration.Duration
import play.api.libs.iteratee._
import org.w3.vs._
import play.api.Mode
import akka.actor.{ ActorSystem => _, _ }

class ResumedRunTest extends VSTestKit with TestData with ServersTest with WipeoutData {

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet()))

  "test cyclic + interruption + resuming job" in {

    val job = TestData.job
    val user = TestData.user

    User.save(TestData.user).getOrFail()
    Job.save(TestData.job).getOrFail()

    val jobId: JobId = job.id
    
    val runningJob = job.run().getOrFail()
    val Running(runId, actorName) = runningJob.status
    runningJob.id should be(job.id)

    // register to the death of the JobActor
    import vs.timeout
    val jobActorRef = vs.system.actorSelection(actorName.actorPath).resolveOne().getOrFail()
    watch(jobActorRef)

    // wait for the first ResourceResponseEvent
    (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent] { case _: ResourceResponseEvent => () }).getOrFail()

    // kill the jobActor
    jobActorRef ! PoisonPill

    // wait for the death notification
    val terminated = fishForMessagePF(Duration("60s")) { case event: Terminated => event }

    // make sure we've seen the right death
    terminated.actor should be(jobActorRef)

    // then resume!
    val rJob = Job.get(jobId).getOrFail()

    // the database should know that the job was still Running
    rJob.status should be(runningJob.status)

    // now we revive the actor
    val resume = rJob.resume().getOrFail()
    resume should be(())

    val completeRunEvent =
      (rJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources should be(circumference + 1)

  }

}
