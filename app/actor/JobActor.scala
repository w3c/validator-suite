package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
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
import message.GetJobData
import scalaz._
import scalaz.Scalaz._
import org.joda.time.DateTime
import org.w3.util.akkaext._

// TODO extract all pure function in a companion object
class JobActor(job: JobConfiguration)(
  implicit val configuration: VSConfiguration)
    extends Actor with FSM[(RunActivity, ExplorationMode), RunData] with Listeners {

  import configuration._

  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[JobActor])

  val http = system.actorFor(system / "http")

  /**
   * A shorten id for logs readability
   */
  val shortId = job.shortId

  implicit def strategy = job.strategy

  // at instanciation of this actor

  // TODO
  configuration.system.scheduler.schedule(10 seconds, 2 seconds, self, 'Tick)

  var lastRunData = RunData.makeFresh(jobId = job.id, strategy = strategy)

  startWith(lastRunData.state, lastRunData)

  def stateOf(data: RunData): State = {
    val currentState = stateName
    val newState = data.state
    val stayOrGoto = if (currentState === newState) stay() else goto(newState)
    stayOrGoto using data
  }

  when((Idle, ProActive))(stateFunction)
  when((Idle, Lazy))(stateFunction)
  when((Busy, ProActive))(stateFunction)
  when((Busy, Lazy))(stateFunction)

  def stateFunction: StateFunction = {
    case Event(message.GetJobData, data) => {
      val jobData = JobData(data)
      sender ! jobData
      stay()
    }
    case Event('Tick, data) => {
      // do it only if necessary (ie. something has changed since last time)
      if (data ne lastRunData) {
        // send to the store a fresher version of this run
        val snapshot = RunSnapshot(data)
        store.putSnapshot(snapshot)
        // tell the subscribers about the current data for this run
        val msg = message.UpdateData(JobData(data))
        tellEverybody(msg)
        lastRunData = data
      }
      stay()
    }
    // TODO makes runId part of the AssertorResult and change logic accordingly
    case Event(result: AssertorResult, data) => {
      logger.debug("%s: %s observed by %s" format (shortId, result.url, result.assertorId))
      val msg = message.NewAssertorResult(result)
      tellEverybody(msg)
      store.putAssertorResult(result)
      val dataWithAssertorResult = data.withAssertorResult(result)
      stateOf(dataWithAssertorResult)
    }
    // TODO makes runId part of the FetchResponse and change logic accordingly
    case Event(fetchResponse: FetchResponse, data) => {
      val (resourceInfo, data2) = receiveResponse(fetchResponse, data)
      store.putResourceInfo(resourceInfo)
      val msg = message.NewResourceInfo(resourceInfo)
      tellEverybody(msg)
      val data3 = scheduleNextURLsToFetch(data2)
      val data4 = {
        val assertors = strategy.assertorsFor(resourceInfo)
        if (assertors.nonEmpty) {
          assertors foreach { assertorId => assertionsActorRef ! AssertorCall(assertorId, resourceInfo) }
          data3.copy(pendingAssertions = data3.pendingAssertions + assertors.size)
        } else {
          data3
        }
      }
      stateOf(data4)
    }
    case Event(msg: ListenerMessage, _) => {
      listenerHandler(msg)
      stay()
    }
    case Event(message.BeProactive, data) => stateOf(data.copy(explorationMode = ProActive))
    case Event(message.BeLazy, data) => stateOf(data.copy(explorationMode = Lazy))
    case Event(message.Refresh, data) => {
      val firstURLs = strategy.seedURLs.toList
      val freshRunData = RunData.makeFresh(job.id, strategy)
      val (runData, _) = freshRunData.withNewUrlsToBeExplored(firstURLs, 0)
      stateOf(scheduleNextURLsToFetch(runData))
    }
    case Event(message.Stop, data) => {
      assertionsActorRef ! message.Stop
      stateOf(data.copy(explorationMode = Lazy, toBeExplored = List.empty))
    }
  }

  onTransition {
    case _ -> _ => {
      val msg = message.UpdateData(JobData(nextStateData))
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
  private final def initialConditions(): RunData = store.latestSnapshotFor(job.id).waitResult() match {
    case None => RunData.makeFresh(jobId = job.id, strategy = strategy)
    case Some(snapshot) => replayEventsOn(RunData(strategy, snapshot), Some(snapshot.createdAt))
  }

  final private def replayEventsOn(_data: RunData, afterOpt: Option[DateTime]): RunData = {
    var data = _data
    store.listResourceInfos(job.id, after = afterOpt).waitResult() foreach { resourceInfo =>
      data = data.withCompletedFetch(resourceInfo.url)
    }
    store.listAssertorResults(job.id, after = afterOpt).waitResult() foreach { assertorResult =>
      data = data.withAssertorResult(assertorResult)
    }
    data
  }

  private final def scheduleNextURLsToFetch(data: RunData): RunData = {
    val (newObservation, explores) = data.takeAtMost(MAX_URL_TO_FETCH)
    val runId = data.runId
    explores foreach { explore => fetch(explore, runId) }
    newObservation
  }

  /**
   * Fetch one URL
   *
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: RunData#Explore, runId: RunId): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    action match {
      case GET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        http ! Fetch(url, GET, runId)
      }
      case HEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        http ! Fetch(url, HEAD, runId)
      }
      case FetchNothing => {
        logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (shortId, url))
      }
    }
  }

  // pure function (no side-effect)
  // TODO do something with runId
  private final def receiveResponse(fetchResponse: FetchResponse, _data: RunData): (ResourceInfo, RunData) = {
    val data = _data.withCompletedFetch(fetchResponse.url)
    fetchResponse match {
      case OkResponse(url, GET, status, headers, body, runId) => {
        logger.debug("%s: GET <<< %s" format (shortId, url))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val (extractedURLs, newDistance) = headers.mimetype collect {
          case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct -> (distance + 1)
          case "text/css" => URLExtractor.fromCSS(url, body).distinct -> distance /* extract links from CSS here*/
        } getOrElse (List.empty, distance)
        // TODO do something with the newUrls
        val (_newData, newUrls) = data.withNewUrlsToBeExplored(extractedURLs, newDistance)
        if (!newUrls.isEmpty)
          logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, newUrls.size, data.numberOfKnownUrls))
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = GET,
          distancefromSeed = distance,
          result = FetchResult(status, headers, extractedURLs))
        (ri, _newData)
      }
      // HEAD
      case OkResponse(url, HEAD, status, headers, _, runId) => {
        logger.debug("%s: HEAD <<< %s" format (shortId, url))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = HEAD,
          distancefromSeed = distance,
          result = FetchResult(status, headers, List.empty))
        (ri, data)
      }
      case KoResponse(url, action, why, runId) => {
        logger.debug("%s: Exception for %s: %s" format (shortId, url, why.getMessage))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = action,
          distancefromSeed = distance,
          result = ResourceInfoError(why.getMessage))
        (ri, data)
      }
    }
  }

}
