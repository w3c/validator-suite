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
  case class Resume(toBeFetched: Iterable[Fetch], toBeAsserted: Iterable[AssertorCall])
  case object GetRunData
  case object GetResourceDatas

  val logger = Logger.of(classOf[JobActor])
//  val logger = new Object {
//    def debug(msg: String): Unit = println("== " + msg)
//    def error(msg: String): Unit = println("== " + msg)
//    def error(msg: String, t: Throwable): Unit = println("== " + msg)
//    def warn(msg: String): Unit = println("== " + msg)
//  }

  def executeCommands(run: Run, runActor: ActorRef, toBeFetched: Iterable[Fetch], toBeAsserted: Iterable[AssertorCall], http: ActorRef, assertionsActorRef: ActorRef)(implicit sender: ActorRef): Unit = {
    
    def fetch(fetch: Fetch): Unit = {
      logger.debug(s"${run.shortId}: >>> ${fetch.method} ${fetch.url}")
      http ! fetch
    }

    if (! toBeFetched.isEmpty) {
      toBeFetched foreach { url => fetch(url) }
    }

    if (! toBeAsserted.isEmpty) {
      toBeAsserted foreach { call => assertionsActorRef ! call }
    }

  }

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
  implicit val strategy = job.strategy

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  startWith(NotYetStarted, initialRun)

  def stateOf(run: Run): State = {
    // if there is still some pending assertions but nore more URLs to explore
    // that's only for debugging purposes
    if (run.pendingAssertions.nonEmpty && run.noMoreUrlToExplore) {
      logger.debug(s"${run.shortId}: Exploration phase finished. Fetched ${run.numberOfFetchedResources} pages")
      stay() using run
    } else if (run.hasNoPendingAction) {
      // if there is no more assertions, that's the end for this Run
      logger.debug(s"${run.shortId}: Assertion phase finished")
      val completeRunEvent = CompleteRunEvent(userId, jobId, run.runId, run.data, run.resourceDatas)
      publish(completeRunEvent)
      stopThisActor()
      goto(Stopping) using run
    } else {
      stay() using run
    }
  }

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
      publish(event)
      // Q: we could make running part of CreateRunEvent
      val running = Running(run.runId, self.path)
      sender ! running
      val (startedRun, toBeFetched) = run.newlyStartedRun
      executeCommands(startedRun, self, toBeFetched, List.empty, httpActorRef, assertionsActorRef)
      runningJobs.inc()
      goto(Started) using startedRun
    }

    case Event(Resume(toBeFetched, toBeAsserted), run) => {
      sender ! ()
      executeCommands(run, self, toBeFetched, toBeAsserted, httpActorRef, assertionsActorRef)
      runningJobs.inc()
      goto(Started)
    }

  }

  when(Started) {

    case Event(ar: AssertorResponse, run) if ar.runId /== run.runId => {
      logger.error(s"${run.shortId} (previous run): received assertor response")
      logger.error(s"assertorResponse.runId = ${ar.runId}")
      logger.error(s"run.runId =              ${run.runId}")
      stay()
    }

    // Run + Event ---> Run' + Actions
    // List[Events] ---> Run + Actions

    case Event(result: AssertorResult, run) => {
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val (newRun, filteredAssertions) = run.withAssertorResult(result)
      val newResult = result.copy(assertions = filteredAssertions)
      val event = AssertorResponseEvent(userId, jobId, run.runId, newResult)
      publish(event)
      stateOf(newRun)
    }

    case Event(failure: AssertorFailure, run) => {
      publish(AssertorResponseEvent(userId, jobId, run.runId, failure))
      //publish(RunUpdate())
      stateOf(run.withAssertorFailure(failure))
    }

    case Event((runId: RunId, response: ResourceResponse), run) if runId /== run.runId => {
      logger.error(s"${run.shortId} (previous run): <<< ${response.url}")
      stay()
    }

    case Event((_: RunId, httpResponse: HttpResponse), run) => {
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      extractedUrls.update(httpResponse.extractedURLs.size)
//      Run.saveEvent(ResourceResponseEvent(run.runId, httpResponse)) onSuccess { case () =>
//        tellEverybody(NewResource(userId, jobId, run.runId, httpResponse, run.data))
//      }
      val event = ResourceResponseEvent(userId, jobId, run.runId, httpResponse)
      publish(event)
      val (newRun, urlsToFetch, assertorCalls) =
        run.withHttpResponse(httpResponse)
      executeCommands(newRun, self, urlsToFetch, assertorCalls, httpActorRef, assertionsActorRef)
      stateOf(newRun)
    }

    case Event((_: RunId, error: ErrorResponse), run) => {
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
//      Run.saveEvent(ResourceResponseEvent(run.runId, error)) onSuccess { case () =>
//        tellEverybody(NewResource(userId, jobId, run.runId, error, run.data))
//      }
      val event = ResourceResponseEvent(userId, jobId, run.runId, error)
      publish(event)
      val (runWithErrorResponse, urlsToFetch) = run.withErrorResponse(error)
      executeCommands(runWithErrorResponse, self, urlsToFetch, Iterable.empty, httpActorRef, assertionsActorRef)
      stateOf(runWithErrorResponse)
    }

    case Event(Cancel, run) => {
      logger.debug(s"${run.shortId}: Cancel")
      val event = CancelRunEvent(userId, jobId, run.runId, run.data, run.resourceDatas)
      publish(event)
      stopThisActor()
      goto(Stopping)
    }

  }

  def stopThisActor(): Unit = {
    context.stop(self)
  }

  def publish(event: RunEvent): Unit = {
    runEventBus.publish(event)
  }

}
