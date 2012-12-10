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

object JobActor {

  val runningJobs: Counter = Metrics.newCounter(classOf[JobActor], "running-jobs")

  val extractedUrls: Histogram = Metrics.newHistogram(classOf[JobActor], "extracted-urls")

  val fetchesPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "fetches-per-run")

  val assertionsPerRun: Histogram = Metrics.newHistogram(classOf[JobActor], "assertions-per-run")

  /* user generated events */
  case object Refresh
  case object Stop
  case object BeProactive
  case object BeLazy
  case object WaitLastWrite

  /* events internal to the application */
  case object GetRun
  case object NoMorePendingAssertion
  case object Resume
  case object GetSnapshot

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

  var lastWrite: Future[Unit] = Future.successful(())

  import conf._

  val userId = job.creatorId
  val jobId = job.id

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)).withDispatcher("user-dispatcher"), "assertions")

  // TODO is it really what we want? I don't think so

  val http: ActorRef = system.actorFor(system / "http")

  implicit val strategy = job.strategy

  // at instanciation of this actor

  if (initialState === Started) {
    executeCommands(initialRun, self, toBeFetched, toBeAsserted, http, assertionsActorRef)
  }

  // TODO
  //conf.system.scheduler.schedule(2 seconds, 2 seconds, self, 'Tick)

  startWith(initialState, initialRun)

  def stateOf(run: Run): State = {

    // T: Should completedOn really be overidden on each call of stateOf?
    val _run =
      if (run.hasNoPendingAction && !run.completedOn.isDefined) {
        val now = DateTime.now(DateTimeZone.UTC)
        lastWrite = Run.saveEvent(CompleteRunEvent(run.userId, run.jobId, run.runId, now))
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

    if (stateData.activity /== _run.activity) {
      _run.activity match {
        case Running => runningJobs.inc()
        case Idle => {
          runningJobs.dec()
          fetchesPerRun.update(_run.numberOfFetchedResources)
          assertionsPerRun.update(_run.assertions.size)
        }
      }
    }

    if (stateData.state /== _run.state) {
      logger.debug(s"${run.shortId}: transition to new state ${_run.state}")
      //val msg = UpdateData(_run.jobData, job.id, _run.activity)
      //tellEverybody(msg)
      if (_run.noMoreUrlToExplore)
        logger.debug(s"${_run.shortId}: Exploration phase finished. Fetched ${_run.numberOfFetchedResources} pages")
      if (_run.pendingAssertions.isEmpty) {
        logger.debug("Assertion phase finished.")
      }
    }
    stay() using _run
  }

  whenUnhandled {

    case Event(Resume, run) => {
      logger.debug(s"${run.shortId}: Resume")
      stay()
    }

    case Event(GetSnapshot, run) => {
      logger.debug(s"${run.shortId}: GetSnapshot")
      sender ! run.jobData
      stay()
    }

    case Event(GetRun, run) => {
      //logger.debug("%s: GetRun" format run.shortId)
      sender ! run
      stay()
    }

    case Event(msg: ListenerMessage, run) => {
      logger.debug(s"${run.shortId}: ListenerMessage")
      listenerHandler(msg)
      stay()
    }

    case Event(WaitLastWrite, _) => {
      sender ! lastWrite
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
      logger.error(s"${run.shortId}: unexpected event ${e}")
      stay()
    }

  }

  when(NeverStarted) {

    case Event(Refresh, run) => {
      lastWrite = Run.saveEvent(CreateRunEvent(run.userId, run.jobId, run.runId, run.strategy, run.createdAt))
      sender ! run.context
      val (startedRun, toBeFetched) = run.newlyStartedRun
      executeCommands(startedRun, self, toBeFetched, List.empty, http, assertionsActorRef)
      runningJobs.inc()
      goto(Started) using startedRun
    }

  }

  when(Started) {
  
    // just ignore this event if from a previous Run
    case Event(ar: AssertorResponse, run) if ar.context /== run.context => {
      logger.debug(s"${run.shortId} (previous run): received assertor response")
      logger.debug(s"assertorResponse.context = ${ar.context}")
      logger.debug(s"run.context =              ${run.context}")
      stay()
    }

    case Event(result: AssertorResult, run) => {
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val now = DateTime.now(DateTimeZone.UTC)
      lastWrite = Run.saveEvent(AssertorResponseEvent(run.runId, result, now))
      val newRun = run.withAssertorResult(result)
      tellEverybody(NewAssertorResult(result, newRun, now))
      stateOf(newRun)
    }

    case Event(failure: AssertorFailure, run) => {
      logger.warn(s"${run.shortId}: ${failure.assertor} failed to assert ${failure.sourceUrl} because [${failure.why}]")
      lastWrite = Run.saveEvent(AssertorResponseEvent(run.runId, failure))
      stateOf(run.withAssertorFailure(failure))
    }

    // just ignore this event if from a previous Run
    case Event((contextRun: RunId, response: ResourceResponse), run) if contextRun /== run.runId => {
      logger.debug("${run.shortId} (previous run): <<< ${response.url}")
      stay()
    }

    case Event((_: RunId, httpResponse: HttpResponse), run) => {
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      extractedUrls.update(httpResponse.extractedURLs.size)
      lastWrite = Run.saveEvent(ResourceResponseEvent(run.runId, httpResponse))
      tellEverybody(NewResource(run.context, httpResponse))
      val (newRun, urlsToFetch, assertorCalls) =
        run.withHttpResponse(httpResponse)
      executeCommands(newRun, self, urlsToFetch, assertorCalls, http, assertionsActorRef)
      stateOf(newRun)
    }

    case Event((_: RunId, error: ErrorResponse), run) => {
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
      lastWrite = Run.saveEvent(ResourceResponseEvent(run.runId, error))
      tellEverybody(NewResource(run.context, error))
      val (runWithErrorResponse, urlsToFetch) = run.withErrorResponse(error)
      executeCommands(runWithErrorResponse, self, urlsToFetch, Iterable.empty, http, assertionsActorRef)
      stateOf(runWithErrorResponse)
    }

    case Event(BeProactive, run) => stateOf(run.withMode(ProActive))

    case Event(BeLazy, run) => stateOf(run.withMode(Lazy))

    case Event(Refresh, run) => {
      logger.debug(s"${run.shortId}: received a Refresh")
      val (startedRun, urlsToBeFetched) = Run.freshRun(userId, jobId, strategy).newlyStartedRun
      sender ! startedRun.context
      executeCommands(startedRun, self, urlsToBeFetched, List.empty, http, assertionsActorRef)
      stateOf(startedRun)
    }

    case Event(Stop, run) => {
      logger.debug(s"${run.shortId}: received a Stop")
      assertionsActorRef ! Stop
      stateOf(run.stopMe())
    }

  }

  private def tellEverybody(msg: RunUpdate): Unit = {
    // tell the user
    context.actorFor("../..") ! msg
    // tell all the listeners
    tellListeners(msg)
  }

}
