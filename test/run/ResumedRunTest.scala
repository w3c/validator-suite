package org.w3.vs.run

import org.w3.vs.util._
import org.w3.vs.util.website._
import org.w3.vs.model._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.util.Util._
import play.api.libs.iteratee._
import org.w3.vs._
import org.w3.vs.util.TestData
import play.api.Mode
import org.w3.vs.model.Running
import org.w3.vs.model.DoneRunEvent
import org.w3.vs.model.ResourceResponseEvent
import akka.actor.{PoisonPill, Terminated}
import org.w3.vs.util.Webserver
import akka.actor.{ActorSystem => AkkaSystem, _}
import org.w3.vs.util.akkaext.PathAware
import org.w3.vs.http.Http.SetSleepTime

class ResumedRunTest extends VSTestKit[ActorSystem with Database with HttpClient with RunEvents](
  new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents
) with ServersTest with TestData {

  //implicit val vs = new ValidatorSuite(Mode.Test) with DefaultActorSystem with DefaultDatabase with DefaultHttpClient with DefaultRunEvents

  //val a: ActorSystem = conf
//
//  val strategy =
//    Strategy(
//      entrypoint=URL("http://localhost:9001/"),
//      linkCheck=true,
//      maxResources = 100,
//      filter=Filter(include=Everything, exclude=Nothing),
//      assertorsConfiguration = Map.empty)
//
//  val job = Job.createNewJob(name = "@@", strategy = strategy, creatorId = userTest.id)

  val circumference = 20
  
  val servers = Seq(Webserver(9001, Website.cyclic(circumference).toServlet()))
  val http = vs.httpActorRef
  PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
  
  "test cyclic + interruption + resuming job" in {

    val job = TestData.job
    val user = TestData.user

    User.save(TestData.user).getOrFail()
    Job.save(TestData.job).getOrFail()

    val jobId = job.id
    
//    PathAware(http, http.path / "localhost_9001") ! SetSleepTime(0)
    import org.w3.vs.util.akkaext._

    val runningJob = job.run().getOrFail()
    val Running(runId, actorPath) = runningJob.status
    runningJob.id must be(job.id)

    // register to the death of the JobActor
    val jobActorRef = vs.system.actorFor(actorPath)
    watch(jobActorRef)

    // wait for the first ResourceResponseEvent
    (runningJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent] { case _: ResourceResponseEvent => () }).getOrFail()

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
      (rJob.runEvents() &> Enumeratee.mapConcat(_.toSeq) |>>> waitFor[RunEvent]{ case e: DoneRunEvent => e }).getOrFail()

    completeRunEvent.resources must be(circumference + 1)

  }
  
}
