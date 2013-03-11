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

  case class Listen[Event](subscriber: ActorRef, classifier: Classifier[Event])
  case class Deafen(subscriber: ActorRef)

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
      case EmitEvent(event) =>
        self ! event
    }
  }

  def saveEvent(event: RunEvent): Future[Unit] = event match {
    case event@CreateRunEvent(userId, jobId, runId, actorPath, strategy, createdAt, timestamp) =>
      Run.saveEvent(event) flatMap { case () =>
        val running = Running(runId, actorPath)
        Job.updateStatus(jobId, status = running)
      }

    case event@DoneRunEvent(userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, timestamp) =>
      Run.saveEvent(event) flatMap { case () =>
        val done = Done(runId, doneReason, timestamp, RunData(resources, errors, warnings, JobDataIdle, Some(timestamp)))
        Job.updateStatus(jobId, status = done, latestDone = done)
      }

    case event@AssertorResponseEvent(userId, jobId, runId, ar, timestamp) =>
      Run.saveEvent(event)

    case event@ResourceResponseEvent(userId, jobId, runId, rr, timestamp) =>
      Run.saveEvent(event)

  }

  def fireSomethingRightAway(subscriber: ActorRef, classifier: Classifier[_], run: Run): Unit = {
    import Classifier._
    classifier match {
      case SubscribeToRunEvent => subscriber ! runEvents
      case SubscribeToRunData => subscriber ! run.data
      case SubscribeToJobData => subscriber ! JobData(job, run)
    }
  }

  // Computes the new state of this actor for a given RunEvent and executes all the side effects
  // (signature could be (State, RunEvent) => State)?
  def handleRunEvent(_run: Run, event: RunEvent): State = {

    // Get the new run and actions resulting from this this event
    // (The ResultStep class isn't really needed here)
    val ResultStep(run, actions) = _run.step(event)

    // remember the RunEvents seen so far
    runEvents :+= event

    // fire runDatas
    publish(run.data)

    // pure side-effect
    executeActions(actions)

    // save event
    futureSideEffect { saveEvent(event) }

    // publish event
    sideEffect { publish(event) }

    // compute the next step and do side-effects
    val state = event match {
      case CreateRunEvent(_, _, _, _, _, _, _) =>
        runningJobs.inc()
        val running = Running(run.runId, self.path)
        // Job.run() is waiting for this value
        val from = sender
        sideEffect { from ! running }
        goto(Started) using run
      case DoneRunEvent(_, _, _, doneReason, _, _, _, _, _) =>
        if (doneReason === Completed)
          logger.debug(s"${run.shortId}: Assertion phase finished")
        stopThisActor()
        goto(Stopping) using run
      case _ =>
        stay() using run
    }

    state
  }

  /* state machine */

  startWith(NotYetStarted, initialRun)

  whenUnhandled {

    case Event(GetRunData, run) =>
      sender ! run.data
      stay()

    case Event(Listen(subscriber, classifier), run) =>
      fireSomethingRightAway(subscriber, classifier, run)
      subscribe(subscriber, classifier)
      stay()

    case Event(Deafen(subscriber), run) =>
      unsubscribe(subscriber)
      stay()

    case Event(e, run) =>
      logger.error(s"${run.shortId}: unexpected event ${e}")
      stay()

  }

  when(Stopping) {

    // we still answer Listen requests in the Stopping state, but we
    // just send the termination message right away. There is no need
    // to subscribe anybody.
    case Event(Listen(subscriber, classifier), run) =>
      sideEffect {
        fireSomethingRightAway(subscriber, classifier, run)
        subscriber ! ()
      }
      stay()

    case Event(e, run) =>
      logger.debug(s"${run.shortId}: received ${e} while Stopping")
      stay()

  }

  when(NotYetStarted) {

    case Event(Start, run) =>
      val event = CreateRunEvent(userId, jobId, run.runId, self.path, job.strategy, job.createdOn)
      handleRunEvent(run, event)

    case Event(Resume(actions), run) =>
      sender ! ()
      runningJobs.inc()
      executeActions(actions)
      goto(Started)

  }

  when(Started) {

    case Event(result: AssertorResult, run) =>
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val fixedAssertorResult = run.fixedAssertorResult(result)
      val event = AssertorResponseEvent(userId, jobId, run.runId, fixedAssertorResult)
      handleRunEvent(run, event)

    case Event(failure: AssertorFailure, run) =>
      val event = AssertorResponseEvent(userId, jobId, run.runId, failure)
      handleRunEvent(run, event)

    case Event((_: RunId, httpResponse: HttpResponse), run) =>
      // logging/monitoring
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      extractedUrls.update(httpResponse.extractedURLs.size)
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, httpResponse)
      handleRunEvent(run, event)

    case Event((_: RunId, error: ErrorResponse), run) =>
      // logging/monitoring
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, error)
      handleRunEvent(run, event)

    case Event(Cancel, run) =>
      // logging/monitoring
      logger.debug(s"${run.shortId}: Cancel")
      // logic
      val event = DoneRunEvent(userId, jobId, run.runId, Cancelled, run.data.resources, run.data.errors, run.data.warnings, run.resourceDatas)
      handleRunEvent(run, event)

    case Event(event: DoneRunEvent, run) =>
      handleRunEvent(run, event)

  }

  def stopThisActor(): Unit = {
    val duration = scala.concurrent.duration.Duration(5, "s")
    sideEffect {
      map.values.flatten.toSet foreach { subscriber: ActorRef =>
        subscriber ! ()
      }
      // we stay a bit longer in the Stopping state. This lets the
      // occasion for pending Enumerators to catch up
      system.scheduler.scheduleOnce(duration, self, PoisonPill)
    }
  }

  /* managing side-effects: use these functions for all side-effects
   * that need to be ordered */

  /** all side-effects are happening against this future. The invariant
    * holds if you use sideEffect and futureSideEffect to schedule the
    * asynchronous side-effects */
  var lastSideEffect: Future[Unit] = Future.successful(())

  def sideEffect(block: => Unit): Unit =
    lastSideEffect = lastSideEffect andThen { case Success(()) => block }

  def futureSideEffect(block: => Future[Unit]): Unit =
    lastSideEffect = lastSideEffect flatMap { case () => block }

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
    val subscribers = map(classifier)
    subscribers foreach { subscriber =>
      subscriber ! event
    }
  }

}
