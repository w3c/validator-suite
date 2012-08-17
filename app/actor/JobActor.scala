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
import scalaz.Scalaz._
import org.joda.time.DateTime
import org.w3.util.akkaext._
import org.joda.time.DateTimeZone

// TODO extract all pure function in a companion object
class JobActor(job: Job)(implicit val conf: VSConfiguration)
extends Actor with FSM[(RunActivity, ExplorationMode), Run] with Listeners {

  import conf._

  val orgId = job.organization
  val jobId = job.id

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  // TODO is it really what we want? I don't think so

  val logger = Logger.of(classOf[JobActor])

  val http = system.actorFor(system / "http")

  lazy val (enumerator, channel) = Concurrent.broadcast[RunUpdate]
  
  implicit def strategy = job.strategy

  // at instanciation of this actor

  // TODO
  conf.system.scheduler.schedule(2 seconds, 2 seconds, self, 'Tick)

  var lastRun: Run = Run.freshRun(orgId, jobId, strategy)._1
 //Run.freshRun(orgId, jobId, strategy)._1 // TODO get last run data from the db?

  startWith(lastRun.state, lastRun)

  def stateOf(run: Run): State = {
    val runId: RunId = run.id._3
    val currentState = stateName
    val newState = run.state
    val stayOrGoto =
      if (currentState === newState) {
        stay()
      } else {
        logger.debug("%s: transition to new state %s" format (run.shortId, newState.toString))
        goto(newState)
      }
    val run2 =
      if (run.noMoreUrlToExplore && run.pendingAssertions == 0) {
        val now = DateTime.now(DateTimeZone.UTC)
        Run.completedAt(run.runUri, now)
        run.copy(completedAt = Some(now))
      } else {
        run
      }
    stayOrGoto using run2
  }

  when((Idle, ProActive))(stateFunction)
  when((Idle, Lazy))(stateFunction)
  when((Running, ProActive))(stateFunction)
  when((Running, Lazy))(stateFunction)

  
  def stateFunction(): StateFunction = {
    case Event(GetRun, run) => {
      // logger.debug("%s: received a GetRun" format shortId)
      sender ! run
      stay()
    }
    case Event(GetJobEnumerator, run) => {
      sender ! enumerator
      stay()
    }
    case Event('Tick, run) => {
      // do it only if necessary (ie. something has changed since last time)
      if (run ne lastRun) {
        // Should really the frequency of broadcast and the frequency of saves be coupled?
        // this is wrong: should just save a new JobData instead
        //Run.save(run)
        // tell the subscribers about the current run for this run
        val msg = UpdateData(run.jobData, job.id, run.activity)
        tellEverybody(msg)
        // ???
        lastRun = run
      }
      stay()
    }
    // just ignore this event if from a previous Run
    case Event(ar: AssertorResponse, run) if ar.context /== run.id => {
      logger.debug("%s (previous run): received assertor response" format (run.shortId))
      stay()
    }
    case Event(result: AssertorResult, run) => {
      val now = DateTime.now(DateTimeZone.UTC)
      Run.saveEvent(run.runUri, AssertorResponseEvent(result, now))
      tellEverybody(NewAssertorResult(result, now))
      stateOf(run.withAssertorResult(result))
    }
    case Event(failure: AssertorFailure, run) => {
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
      if (! urlsToFetch.isEmpty)
        urlsToFetch foreach { url => fetch(url, run.id._3) }
      if (! assertorCalls.isEmpty)
        assertorCalls foreach { call => assertionsActorRef ! call }
      stateOf(newRun)
    }
    case Event((_: RunId, error: ErrorResponse), run) => {
      logger.debug("%s: <<< error when fetching %s" format (run.shortId, error.url))
      Run.saveEvent(run.runUri, ResourceResponseEvent(error))
      tellEverybody(NewResource(run.id, error))
      stateOf(run)
    }
    case Event(msg: ListenerMessage, _) => {
      listenerHandler(msg)
      stay()
    }
    case Event(BeProactive, run) => stateOf(run.withMode(ProActive))
    case Event(BeLazy, run) => stateOf(run.withMode(Lazy))
    case Event(Refresh, run) => {
      logger.debug("%s: received a Refresh" format run.shortId)
      val (freshRun, urlsToBeFetched) = Run.freshRun(orgId, jobId, strategy)
      sender ! freshRun.id
      urlsToBeFetched foreach { url => fetch(url, freshRun.id._3) }
      stateOf(freshRun)
    }
    case Event(Stop, run) => {
      logger.debug("%s: received a Stop" format run.shortId)
      assertionsActorRef ! Stop
      stateOf(run.stopMe())
    }
    case Event(a, run) => {
      logger.error("uncatched event: " + a)
      stay()
    }
  }

  onTransition {
    case _ -> _ => {
      val msg = UpdateData(nextStateData.jobData, job.id, nextStateData.activity)
      tellEverybody(msg)
      if (nextStateData.noMoreUrlToExplore) {
        logger.info("%s: Exploration phase finished. Fetched %d pages" format (nextStateData.shortId, nextStateData.fetched.size))
        if (nextStateData.pendingAssertions == 0) {
          logger.info("Assertion phase finished.")
        }
      }
    }
  }

  private final def tellEverybody(msg: Any): Unit = {
    // tell the organization
    context.actorFor("../..") ! msg
    // tell all the listeners
    tellListeners(msg)
  }

  /**
   * Fetch one URL
   *
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  // TODO Run.withHttpResponse should return a List[Fetch] instead of List[URL]
  private final def fetch(url: URL, runId: RunId): Unit = {
    val currentRun = stateData
    val action = strategy.getActionFor(url)
    action match {
      case GET => {
        logger.debug("%s: >>> GET %s" format (currentRun.shortId, url))
        http ! Fetch(url, GET, (job.vo.organization, job.id, runId))
      }
      case HEAD => {
        logger.debug("%s: >>> HEAD %s" format (currentRun.shortId, url))
        http ! Fetch(url, HEAD, (job.vo.organization, job.id, runId))
      }
      case IGNORE => {
        logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (currentRun.shortId, url))
      }
    }
  }

}
