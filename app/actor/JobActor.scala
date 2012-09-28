package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import org.w3.vs.actor.message._
import akka.actor._
import play.api.libs.iteratee._
import play.Logger
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import org.w3.util.Headers.wrapHeaders
import scalaz._
import scalaz.Scalaz._
import org.joda.time.DateTime
import org.w3.util.akkaext._
import org.joda.time.DateTimeZone

object JobActor {

  /* user generated events */
  case object Refresh
  case object Stop
  case object BeProactive
  case object BeLazy

  /* events internal to the application */
  case object GetRun
  case object NoMorePendingAssertion
  case object Resume
  case object GetSnapshot

  val logger = Logger.of(classOf[JobActor])

  def executeCommands(run: Run, runActor: ActorRef, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall], http: ActorRef, assertionsActorRef: ActorRef)(implicit sender: ActorRef): Unit = {
    
    def fetch(url: URL): Unit = {
      val action = run.strategy.getActionFor(url)
      action match {
        case GET => {
          logger.debug("%s: >>> GET %s" format (run.shortId, url))
          http ! Http.Fetch(url, GET, run.id._3)
        }
        case HEAD => {
          logger.debug("%s: >>> HEAD %s" format (run.shortId, url))
          http ! Http.Fetch(url, HEAD, run.id._3)
        }
        case IGNORE => {
          logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (run.shortId, url))
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
case object Started extends JobActorState
case object NeverStarted extends JobActorState

// TODO extract all pure function in a companion object
class JobActor(
  job: Job,
  initialState: JobActorState,
  initialRun: Run,
  toBeFetched: Iterable[URL],
  toBeAsserted: Iterable[AssertorCall])(
  implicit val conf: VSConfiguration)
extends Actor with FSM[JobActorState, Run] with Listeners {

  import conf._

  val orgId = job.organization
  val jobId = job.id

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  // TODO is it really what we want? I don't think so

  val http: ActorRef = system.actorFor(system / "http")

  implicit val strategy = job.strategy

  // at instanciation of this actor

  if (initialState === Started) executeCommands(initialRun, self, toBeFetched, toBeAsserted, http, assertionsActorRef)

  // TODO
  //conf.system.scheduler.schedule(2 seconds, 2 seconds, self, 'Tick)

  startWith(initialState, initialRun)

  def stateOf(run: Run): State = {

    // T: Should completedOn really be overidden on each call of stateOf?
    val _run =
      if (run.hasNoPendingAction && !run.completedOn.isDefined) {
        val now = DateTime.now(DateTimeZone.UTC)
        Run.complete(job.jobUri, run.runUri, now)
        val msg = RunCompleted(job.id, now)
        tellEverybody(msg)
        run.copy(completedOn = Some(now))
      } else {
        run
      }

    // Compare the state data and not the state of the fsm to tell if we must broadcast
    // sameAs() compares jobData objects ignoring createdAt
    if (!_run.jobData.sameAs(stateData.jobData)) {
      val msg = UpdateData(job.id, _run.jobData, _run.activity)
      tellEverybody(msg)
    }

    if (stateData.state /== _run.state) {
      logger.debug("%s: transition to new state %s" format (run.shortId, _run.state.toString))
      //val msg = UpdateData(_run.jobData, job.id, _run.activity)
      //tellEverybody(msg)
      if (_run.noMoreUrlToExplore)
        logger.info("%s: Exploration phase finished. Fetched %d pages" format (_run.shortId, _run.numberOfFetchedResources))
      if (_run.pendingAssertions == 0)
        logger.info("Assertion phase finished.")
    }
    stay() using _run
  }

  whenUnhandled {

    case Event(Resume, run) => {
      logger.debug("%s: Resume" format run.shortId)
      stay()
    }

    case Event(GetSnapshot, run) => {
      logger.debug("%s: GetSnapshot" format run.shortId)
      sender ! run.jobData
      stay()
    }

    case Event(GetRun, run) => {
      logger.debug("%s: GetRun" format run.shortId)
      sender ! run
      stay()
    }

    case Event(msg: ListenerMessage, run) => {
      logger.debug("%s: ListenerMessage" format run.shortId)
      listenerHandler(msg)
      stay()
    }

    // this may not be needed
    /*case Event('Tick, run) => {
      // do it only if necessary (ie. something has changed since last time)
      if (run ne stateData) {
        // save jobData?
        // tell the subscribers about the current run for this run
        val msg = UpdateData(run.jobData, job.id, run.activity)
        tellEverybody(msg)
      }
      stay()
    }*/

    case Event(e, run) => {
      logger.error("%s: unexpected event %s" format (run.shortId, e.toString))
      stay()
    }

  }

  when(NeverStarted) {

    case Event(Refresh, run) => {
      Run.save(run)
      sender ! run.id
      val (startedRun, toBeFetched) = run.newlyStartedRun
      executeCommands(startedRun, self, toBeFetched, List.empty, http, assertionsActorRef)
      goto(Started) using startedRun
    }

  }

  when(Started) {
  
    // just ignore this event if from a previous Run
    case Event(ar: AssertorResponse, run) if ar.context /== run.id => {
      logger.debug("%s (previous run): received assertor response" format (run.shortId))
      logger.debug("assertorResponse.context = "+ar.context)
      logger.debug("run.id =                   "+run.id)
      stay()
    }

    case Event(result: AssertorResult, run) => {
      logger.debug("%s: %s produced AssertorResult for %s" format (run.shortId, result.assertor, result.sourceUrl.toString))
      val now = DateTime.now(DateTimeZone.UTC)
      Run.saveEvent(run.runUri, AssertorResponseEvent(result, now))
      tellEverybody(NewAssertorResult(result, now))
      stateOf(run.withAssertorResult(result))
    }

    case Event(failure: AssertorFailure, run) => {
      logger.debug("%s: %s failed to assert %s because [%s]" format (run.shortId, failure.assertor, failure.sourceUrl.toString, failure.why))
      Run.saveEvent(run.runUri, AssertorResponseEvent(failure))
      stateOf(run.withAssertorFailure(failure))
    }

    // just ignore this event if from a previous Run
    case Event((contextRun: RunId, response: ResourceResponse), run) if contextRun /== run.id._3 => {
      logger.debug("%s (previous run): <<< %s" format (run.shortId, response.url))
      stay()
    }

    case Event((_: RunId, httpResponse: HttpResponse), run) => {
      logger.debug("%s: <<< %s" format (run.shortId, httpResponse.url))
      Run.saveEvent(run.runUri, ResourceResponseEvent(httpResponse))
      tellEverybody(NewResource(run.id, httpResponse))
      val (newRun, urlsToFetch, assertorCalls) =
        run.withHttpResponse(httpResponse)
      executeCommands(newRun, self, urlsToFetch, assertorCalls, http, assertionsActorRef)
      stateOf(newRun)
    }

    case Event((_: RunId, error: ErrorResponse), run) => {
      logger.debug("%s: <<< error when fetching %s" format (run.shortId, error.url))
      Run.saveEvent(run.runUri, ResourceResponseEvent(error))
      tellEverybody(NewResource(run.id, error))
      val runWithErrorResponse = run.withErrorResponse(error)
      stateOf(runWithErrorResponse)
    }

    case Event(BeProactive, run) => stateOf(run.withMode(ProActive))

    case Event(BeLazy, run) => stateOf(run.withMode(Lazy))

    case Event(Refresh, run) => {
      logger.debug("%s: received a Refresh" format run.shortId)
      val (startedRun, urlsToBeFetched) = Run.freshRun(orgId, jobId, strategy).newlyStartedRun
      sender ! startedRun.id
      executeCommands(startedRun, self, urlsToBeFetched, List.empty, http, assertionsActorRef)
      stateOf(startedRun)
    }

    case Event(Stop, run) => {
      logger.debug("%s: received a Stop" format run.shortId)
      assertionsActorRef ! Stop
      stateOf(run.stopMe())
    }

  }

  private def tellEverybody(msg: RunUpdate): Unit = {
    // tell the organization
    context.actorFor("../..") ! msg
    // tell all the listeners
    tellListeners(msg)
  }

}
