package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import akka.actor._
import play.Logger
import org.w3.vs.http._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import com.yammer.metrics._
import com.yammer.metrics.core._
import scalaz.Equal
import scalaz.Scalaz._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._

object JobActor {

  val runningJobs: Counter = Metrics.newCounter(classOf[JobActor], "running-jobs")

  val extractedUrls: Histogram = Metrics.newHistogram(classOf[JobActor], "extracted-urls")

  val fetchesPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "fetches-per-run")

  val assertionsPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "assertions-per-run")

  /* actor events */
  case object Start
  case object Cancel
  case class Resume(actions: Iterable[RunAction])
  case object GetRunData
  case object GetResourceDatas

  val logger = Logger.of(classOf[JobActor])
//  val logger = new Object {
//    def debug(msg: String): Unit = println("== " + msg)
//    def error(msg: String): Unit = println("== " + msg)
//    def error(msg: String, t: Throwable): Unit = println("== " + msg)
//    def warn(msg: String): Unit = println("== " + msg)
//  }

}

import JobActor._

object JobActorState {
  implicit val equal = Equal.equalA[JobActorState]
}

sealed trait JobActorState
/** already started */
case object Started extends JobActorState
/** waiting for either Start or Resume event */
case object NotYetStarted extends JobActorState
/** stopping an Actor is an asynchronous operation, so we may have to deal with messages in the meantime */
case object Stopping extends JobActorState

// TODO extract all pure function in a companion object
class JobActor(job: Job, initialRun: Run)(implicit val conf: VSConfiguration)
extends Actor with FSM[JobActorState, Run] {

  import conf._

  val userId = job.creatorId
  val jobId = job.id
  val runId = initialRun.runId
  implicit val strategy = job.strategy

  // TODO: change the way it's done here
  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  /* functions */

  def executeActions(actions: Iterable[RunAction]): Unit = {
    actions foreach {
      case fetch: Fetch =>
        logger.debug(s"${runId.shortId}: >>> ${fetch.method} ${fetch.url}")
        httpActorRef ! fetch
      case call: AssertorCall =>
        assertionsActorRef ! call
    }
  }

  def saveEvent(event: RunEvent): Future[Unit] = event match {
    case event@CreateRunEvent(userId, jobId, runId, actorPath, strategy, createdAt, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val running = Running(runId, actorPath)
        Job.updateStatus(jobId, status = running)
      }
    }
    case event@CompleteRunEvent(userId, jobId, runId, runData, resourceDatas, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val done = Done(runId, Completed, timestamp, runData)
        Job.updateStatus(jobId, status = done, latestDone = done)
      }
    }
    case event@CancelRunEvent(userId, jobId, runId, runData, resourceDatas, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val done = Done(runId, Cancelled, timestamp, runData)
        Job.updateStatus(jobId, status = done, latestDone = done)
      }
    }
    case event@AssertorResponseEvent(userId, jobId, runId, ar, timestamp) => {
      Run.saveEvent(event)
    }
    case event@ResourceResponseEvent(userId, jobId, runId, rr, timestamp) => {
      Run.saveEvent(event)
    }
  }

  /** does in one single place all the side-effects resulting from a
    * ResultStep
    */
  def handleResultStep(resultStep: ResultStep): State = {
    import resultStep.{ run, actions }

    executeActions(actions)

    // remember the RunEvents seen so far
    runEvents ++= resultStep.events

    // the next state for the JobActor's state machine
    var state = stay() using run

    // fire jobDatas
    JobData.toFire(job, resultStep) foreach { jobData =>
      publish(jobData)
    }

    resultStep.events foreach { event =>
      // save event
      val f = saveEvent(event)
      // publish the event when it's actually saved
      f onSuccess { case () => publish(event) }
      // compute the next step and do side-effects
      event match {
        case CreateRunEvent(_, _, _, _, _, _, _) =>
          runningJobs.inc()
          val running = Running(run.runId, self.path)
          sender ! running
          state = goto(Started) using run
        case CompleteRunEvent(_, _, _, _, _, _) =>
          logger.debug(s"${run.shortId}: Assertion phase finished")
          stopThisActor()
          state = goto(Stopping) using run
        case CancelRunEvent(_, _, _, _, _, _) =>
          stopThisActor()
          state = goto(Stopping) using run
        case _ => ()
      }
    }

    state
  }

  /* state machine */

  startWith(NotYetStarted, initialRun)

  whenUnhandled {

    case Event(GetRunData, run) => {
      sender ! run.data
      stay()
    }

    case Event(e, run) => {
      logger.error(s"${run.shortId}: unexpected event ${e}")
      stay()
    }

  }

  when(Stopping) {

    case Event(e, run) => {
      logger.debug(s"${run.shortId}: received ${e} while Stopping")
      stay()
    }

  }

  when(NotYetStarted) {

    case Event(Start, run) => {
      val event = CreateRunEvent(userId, jobId, run.runId, self.path, job.strategy, job.createdOn)
      handleResultStep(run.step(event))
    }

    case Event(Resume(actions), run) => {
      sender ! ()
      runningJobs.inc()
      executeActions(actions)
      goto(Started)
    }

  }

  when(Started) {

    case Event(result: AssertorResult, run) => {
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val event = AssertorResponseEvent(userId, jobId, run.runId, result)
      handleResultStep(run.step(event))
    }

    case Event(failure: AssertorFailure, run) => {
      val event = AssertorResponseEvent(userId, jobId, run.runId, failure)
      handleResultStep(run.step(event))
    }

    case Event((_: RunId, httpResponse: HttpResponse), run) => {
      // logging/monitoring
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      extractedUrls.update(httpResponse.extractedURLs.size)
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, httpResponse)
      handleResultStep(run.step(event))
    }

    case Event((_: RunId, error: ErrorResponse), run) => {
      // logging/monitoring
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, error)
      handleResultStep(run.step(event))
    }

    case Event(Cancel, run) => {
      // logging/monitoring
      logger.debug(s"${run.shortId}: Cancel")
      // logic
      val event = CancelRunEvent(userId, jobId, run.runId, run.data, run.resourceDatas)
      handleResultStep(run.step(event))
    }

  }

  def stopThisActor(): Unit = {
    context.stop(self)
  }

  /* some variables */

  var runEvents: Vector[RunEvent] = Vector.empty

  /* EventBus */

  var map: Map[Classifier[_], Set[ActorRef]] =
    Classifier.all.map(c => c -> Set.empty[ActorRef]).toMap

  def subscribe(subscriber: ActorRef, classifier: Classifier[_]): Boolean = {
    map.get(classifier) match {
      case Some(subscribers) =>
        map += (classifier -> (subscribers + subscriber))
        true
      case None =>
        false
    }
  }

  def subscribe(subscriber: ActorRef): Unit = {
    map.keys foreach { c =>
      subscribe(subscriber, c)
    }
  }

  def unsubscribe(subscriber: ActorRef, classifier: Classifier[_]): Boolean = {
    map.get(classifier) match {
      case Some(subscribers) =>
        map += (classifier -> (subscribers - subscriber))
        true
      case None => false
    }
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    map foreach { case (classifier, subscribers) =>
      if (subscribers.contains(subscriber)) {
        map += (classifier -> (subscribers - subscriber))
      }
    }
  }

  /** this differs from akka.event.BusEvent as we want to make sure that
    * a classifier exists for Event
    */
  def publish[Event](event: Event)(implicit classifier: Classifier[Event]): Unit = {
    if (event.isInstanceOf[RunEvent])
      runEventBus.publish(event.asInstanceOf[RunEvent])
    val subscribers = map(classifier)
    subscribers foreach { subscriber =>
      subscriber ! event
    }
  }

}
