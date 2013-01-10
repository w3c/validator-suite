package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.actor.message._
import akka.actor._
import play.Logger
import org.w3.vs.http._
import org.joda.time.DateTime
import org.w3.util.akkaext._
import org.joda.time.DateTimeZone
import com.yammer.metrics._
import com.yammer.metrics.core._
import scalaz.Equal
import scalaz.Scalaz._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object JobActor {

  val runningJobs: Counter = Metrics.newCounter(classOf[JobActor], "running-jobs")

  val extractedUrls: Histogram = Metrics.newHistogram(classOf[JobActor], "extracted-urls")

  val fetchesPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "fetches-per-run")

  val assertionsPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "assertions-per-run")

  /* actor events */
  case object Start
  case object Cancel
  case class Resume(toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall])
  case object GetJobData

  val logger = Logger.of(classOf[JobActor])
//  val logger = new Object {
//    def debug(msg: String): Unit = println("== " + msg)
//    def error(msg: String): Unit = println("== " + msg)
//    def warn(msg: String): Unit = println("== " + msg)
//  }

  def executeCommands(run: Run, runActor: ActorRef, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall], http: ActorRef, assertionsActorRef: ActorRef)(implicit sender: ActorRef): Unit = {
    
    def fetch(url: URL): Unit = {
      val action = run.strategy.getActionFor(url)
      action match {
        case GET => {
          logger.debug(s"${run.shortId}: >>> GET ${url}")
          http ! Http.Fetch(url, GET, run.runId)
        }
        case HEAD => {
          logger.debug(s"${run.shortId}: >>> HEAD ${url}")
          http ! Http.Fetch(url, HEAD, run.runId)
        }
        case IGNORE => {
          logger.debug(s"${run.shortId}: Ignoring ${url}. If you're here, remember that you have to remove that url is not pending anymore...")
        }
      }
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
extends Actor with FSM[JobActorState, Run] with Listeners {

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
      val now = DateTime.now(DateTimeZone.UTC)
      val completeRunEvent = CompleteRunEvent(run.userId, run.jobId, run.runId, now)
      Run.saveEvent(completeRunEvent) onSuccess { case () =>
        tellEverybody(RunCompleted(job.id, now))
      }
      stopThisActor()
      goto(Stopping) using run
    } else if (!run.jobData.sameAs(stateData.jobData)) {
      // Compare the state data and not the state of the fsm to tell if we must broadcast
      // sameAs() compares jobData objects ignoring createdAt
      val msg = UpdateData(job.id, run.jobData)
      tellEverybody(msg)
      stay() using run
    } else {
      stay() using run
    }
  }

  whenUnhandled {

    case Event(GetJobData, run) => {
      sender ! run.jobData
      stay()
    }

    case Event(msg: ListenerMessage, run) => {
      logger.debug(s"${run.shortId}: ListenerMessage")
      listenerHandler(msg)
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
      val (startedRun, toBeFetched) = run.newlyStartedRun
      executeCommands(startedRun, self, toBeFetched, List.empty, httpActorRef, assertionsActorRef)
      runningJobs.inc()
      goto(Started) using startedRun
    }

    case Event(Resume(toBeFetched, toBeAsserted), run) => {
      executeCommands(run, self, toBeFetched, toBeAsserted, httpActorRef, assertionsActorRef)
      runningJobs.inc()
      goto(Started)
    }

  }

  when(Started) {

    case Event(ar: AssertorResponse, run) if ar.context /== run.context => {
      logger.error(s"${run.shortId} (previous run): received assertor response")
      logger.error(s"assertorResponse.context = ${ar.context}")
      logger.error(s"run.context =              ${run.context}")
      stay()
    }

    case Event(result: AssertorResult, run) => {
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val now = DateTime.now(DateTimeZone.UTC)
      val newRun = run.withAssertorResult(result)
      Run.saveEvent(AssertorResponseEvent(run.runId, result, now)) onSuccess { case () =>
        tellEverybody(NewAssertorResult(result, newRun, now))
      }
      stateOf(newRun)
    }

    case Event(failure: AssertorFailure, run) => {
      // TODO dispatch AssertorFailure as well?
      Run.saveEvent(AssertorResponseEvent(run.runId, failure))
      stateOf(run.withAssertorFailure(failure))
    }

    case Event((contextRun: RunId, response: ResourceResponse), run) if contextRun /== run.runId => {
      logger.error(s"${run.shortId} (previous run): <<< ${response.url}")
      stay()
    }

    case Event((_: RunId, httpResponse: HttpResponse), run) => {
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      extractedUrls.update(httpResponse.extractedURLs.size)
      Run.saveEvent(ResourceResponseEvent(run.runId, httpResponse)) onSuccess { case () =>
        tellEverybody(NewResource(run.context, httpResponse))
      }
      val (newRun, urlsToFetch, assertorCalls) =
        run.withHttpResponse(httpResponse)
      executeCommands(newRun, self, urlsToFetch, assertorCalls, httpActorRef, assertionsActorRef)
      stateOf(newRun)
    }

    case Event((_: RunId, error: ErrorResponse), run) => {
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
      Run.saveEvent(ResourceResponseEvent(run.runId, error)) onSuccess { case () =>
        tellEverybody(NewResource(run.context, error))
      }
      val (runWithErrorResponse, urlsToFetch) = run.withErrorResponse(error)
      executeCommands(runWithErrorResponse, self, urlsToFetch, Iterable.empty, httpActorRef, assertionsActorRef)
      stateOf(runWithErrorResponse)
    }

    case Event(Cancel, run) => {
      logger.debug(s"${run.shortId}: Cancel")
      Run.saveEvent(CancelEvent(run.runId)) onSuccess { case () =>
        tellEverybody(RunCancelled(job.id))
      }
      stopThisActor()
      goto(Stopping)
    }

  }

  def stopThisActor(): Unit = {
    context.stop(assertionsActorRef)
    context.stop(self)
  }

  private def tellEverybody(msg: RunUpdate): Unit = {
    // tell the user
    usersActorRef ! UsersActor.Forward(msg = msg, to = userId)
    // tell all the listeners
    tellListeners(msg)
  }

}
