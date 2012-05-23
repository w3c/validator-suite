package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import org.w3.vs.actor.message._
import akka.dispatch._
import akka.actor._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration
import scala.collection.mutable.LinkedList
import scala.collection.mutable.LinkedHashMap
import play.api.libs.iteratee.PushEnumerator
import org.w3.util.Headers.wrapHeaders
import akka.pattern.pipe
import scalaz._
import scalaz.Scalaz._
import org.joda.time.DateTime
import org.w3.util.akkaext._

// TODO extract all pure function in a companion object
class JobActor(job: Job)(
  implicit val configuration: VSConfiguration)
    extends Actor with FSM[(RunActivity, ExplorationMode), Run] with Listeners {

  import configuration._

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[JobActor])

  val http = system.actorFor(system / "http")

  /**
   * A shorten id for logs readability
   */
  def shortId: String = job.id.shortId + "/" + stateData.id.shortId

  implicit def strategy = job.strategy

  // at instanciation of this actor

  // TODO
  configuration.system.scheduler.schedule(10 seconds, 2 seconds, self, 'Tick)

  var lastRun = Run(job)

  startWith(lastRun.state, lastRun)

  def stateOf(run: Run): State = {
    val currentState = stateName
    val newState = run.state
    val stayOrGoto =
      if (currentState === newState) {
        stay()
      } else {
        logger.debug("%s: transition to new state %s" format (shortId, newState.toString))
        goto(newState)
      }
    stayOrGoto using run
  }

  when((Idle, ProActive))(stateFunction)
  when((Idle, Lazy))(stateFunction)
  when((Running, ProActive))(stateFunction)
  when((Running, Lazy))(stateFunction)


  
  def stateFunction: StateFunction = {
    case Event(GetJobData, run) => {
      sender ! run.data
      stay()
    }
    case Event('Tick, run) => {
      // do it only if necessary (ie. something has changed since last time)
      if (run ne lastRun) {
        run.save()
        // tell the subscribers about the current run for this run
        val msg = UpdateData(run.data)
        tellEverybody(msg)
        lastRun = run
      }
      stay()
    }
    case Event(response: AssertorResponse, _run) => {
      //logger.debug("%s: %s observed by %s" format (shortId, result.sourceUrl, result.assertorId))
      response match {
        case result: AssertorResult => result.save()
        case _ => ()
      }
      if (response.runId === _run.id) {
        tellEverybody(NewAssertorResponse(response))
        stateOf(_run.withAssertorResponse(response))
      } else {
        stay() // log maybe?
      }
    }
    case Event(response: ResourceResponse, _run) => {
      //println("@@@ "+resourceInfo.toTinyString)
      response.save()
      response match {
        case resource: HttpResponse if response.runId === _run.id => {
          tellEverybody(NewResource(resource))
          _run.explorationMode match {
            case ProActive => {
              val distance = _run.distance.get(resource.url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format resource.url)
              // TODO do something with the newUrls
              val (run, newUrls) = _run.withNewUrlsToBeExplored(resource.extractedURLs, distance + 1)
              if (!newUrls.isEmpty) logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, newUrls.size, run.numberOfKnownUrls))
              val assertors = strategy.assertorsFor(resource)
              stateOf( scheduleNextURLsToFetch {
                if (assertors.nonEmpty) {
                  assertors foreach { 
                    case assertor: FromHttpResponseAssertor => assertionsActorRef ! AssertorCall(assertor, resource)
                    case _ => logger.error("With the current model and logic jobActor only supports FromHttpResponseAssertor")
                  }
                  run.copy(pendingAssertions = run.pendingAssertions + assertors.size)
                } else {
                  run
                }
              })
            }
            case Lazy => stay()
          }
        }
        case _ => stay()
      }
    }
    case Event(msg: ListenerMessage, _) => {
      listenerHandler(msg)
      stay()
    }
    case Event(BeProactive, run) => stateOf(run.withMode(ProActive))
    case Event(BeLazy, run) => stateOf(run.withMode(Lazy))
    case Event(Refresh, run) => {
      val firstURLs = List(strategy.entrypoint)
      val freshRun = Run(job)
      val (runData, _) = freshRun.withNewUrlsToBeExplored(firstURLs, 0)
      stateOf(scheduleNextURLsToFetch(runData))
    }
    case Event(Stop, run) => {
      assertionsActorRef ! Stop
      stateOf(run.stopMe())
    }
  }

  onTransition {
    case _ -> _ => {
      val msg = UpdateData(nextStateData.data)
      tellEverybody(msg)
      if (nextStateData.noMoreUrlToExplore) {
        logger.info("%s: Exploration phase finished. Fetched %d pages" format (shortId, nextStateData.fetched.size))
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
   * tries to get the latest snapshot, then replays the events that happened on it
   */
//  private final def initialConditions(): Run = store.latestSnapshotFor(job.id).waitResult() match {
//    case None => Run.makeFresh(jobId = job.id, strategy = strategy)
//    case Some(snapshot) => replayEventsOn(Run(strategy, snapshot), Some(snapshot.createdAt))
//  }

//  final private def replayEventsOn(_run: Run, afterOpt: Option[DateTime]): Run = {
//    var run = _run
//    store.listResourceInfos(job.id, after = afterOpt).waitResult() foreach { resourceInfo =>
//      run = run.withCompletedFetch(resourceInfo.url)
//    }
//    store.listAssertorResponses(job.id, after = afterOpt).waitResult() foreach { assertorResult =>
//      run = run.withAssertorResponse(assertorResult)
//    }
//    run
//  }

  private final def scheduleNextURLsToFetch(run: Run): Run = {
    val (newObservation, explores) = run.takeAtMost(MAX_URL_TO_FETCH)
    //val runId = run.id
    explores foreach { explore => fetch(explore, run.id) }
    newObservation
  }

  /**
   * Fetch one URL
   *
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: Run#Explore, runId: RunId): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    action match {
      case GET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        http ! Fetch(url, GET, runId, job.id)
      }
      case HEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        http ! Fetch(url, HEAD, runId, job.id)
      }
      case IGNORE => {
        logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (shortId, url))
      }
    }
  }

}
