package org.w3.vs.run

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import akka.dispatch._
import akka.actor._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger
import System.{currentTimeMillis => now}
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
import Scalaz._
import org.joda.time.DateTime

case object Unique

class JobActor(job: JobConfiguration)(implicit val configuration: VSConfiguration) extends Actor with FSM[Unit, RunData] {
  
  import configuration._
  
  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[JobActor])
  
  /**
   * A shorten id for logs readability
   */
  val shortId = job.shortId
  
  implicit def strategy = job.strategy
  
  final private def replayEventsOn(_data: RunData, afterOpt: Option[DateTime]): RunData = {
    var data = _data
    store.listResourceInfos(job.id, after = afterOpt).fold( t => throw t, ris => ris) foreach { resourceInfo =>
      data = data.withCompletedFetch(resourceInfo.url)
    }
    store.listAssertorResults(job.id, after = afterOpt).fold( t => throw t, ars => ars) foreach { assertorResult =>
      data = data.withAssertorResult(assertorResult)
    }
    data
  }
  
  /**
   * tries to get the latest snapshot, then replays the events that happened on it
   */
  private final def initialConditions: RunData = store.latestSnapshotFor(job.id) match {
    case Failure(t) => throw t
    case Success(None) => RunData(jobId = job.id, strategy = strategy)
    case Success(Some(snapshot)) => replayEventsOn(RunData(strategy, snapshot), Some(snapshot.createdAt))
  }
  
  // at instanciation of this actor

  configuration.system.scheduler.schedule(2 seconds, 2 seconds, self, message.TellTheWorldYouAreAlive)

  startWith(Unique, initialConditions)

  when( () ) {
    case Event(message.GetJobData, data) => {
      val jobData = JobData(data)
      sender ! jobData
      stay()
    }
    case Event(message.TellTheWorldYouAreAlive, data) => {
      // send to the store a fresher version of this run
      // TODO: do it only if necessary
      val snapshot = RunSnapshot(data)
      store.putSnapshot(snapshot)
      // tell the subscribers about the current data for this run
      // TODO: do it only if necessary
      val jobData = JobData(data)
      broadcast(message.UpdateData(jobData), data)
      //logger.info("%s: current data - %s" format (shortId, jobData.toString))
      stay()
    }
    // TODO makes runId part of the AssertorResult and change logic accordingly
    case Event(result: AssertorResult, data) => {
      logger.debug("%s: %s observed by %s" format (shortId, result.url, result.assertorId))
      broadcast(message.NewAssertorResult(result), data)
      store.putAssertorResult(result)
      val dataWithAssertorResult = data.withAssertorResult(result)
      stay() using dataWithAssertorResult
    }
    // TODO makes runId part of the FetchResponse and change logic accordingly
    case Event(fetchResponse: FetchResponse, data) => {
      val (resourceInfo, data2) = receiveResponse(fetchResponse, data)
      store.putResourceInfo(resourceInfo)
      broadcast(message.NewResourceInfo(resourceInfo), data2)
      val data3 = scheduleNextURLsToFetch(data2)
      val data4 = scheduleAssertion(resourceInfo, data3)
      stay() using data4
    }
    case Event(message.Subscribe, data) => {
      val subscriber = sender
      val dataWithSubscriber = data.subscribers + subscriber
      logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, dataWithSubscriber.mkString("{", ",", "}")))
      stay() using data.copy(subscribers = dataWithSubscriber)
    }
    case Event(message.Unsubscribe, data) => {
      val subscriber = sender
      val dataWithoutSubscriber = data.subscribers - subscriber
      logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, dataWithoutSubscriber.mkString("{", ",", "}")))
      stay() using data.copy(subscribers = dataWithoutSubscriber)
    }
    case Event(message.BeProactive, data) => stay() using data.copy(explorationMode = ProActive)
    case Event(message.BeLazy, data) => stay() using data.copy(explorationMode = Lazy)
    case Event(message.Refresh, data) => stay() using scheduleNextURLsToFetch(initialData)
    case Event(message.Stop, data) => stay() using data.copy(explorationMode = Lazy, toBeExplored = List.empty)
  }

  onTransition {
    // detect when the status as changed
    case (_, _) if RunData.somethingImportantHappened(stateData, nextStateData) => {
      val jobData = JobData(nextStateData)
      broadcast(message.UpdateData(jobData), nextStateData)
      if (nextStateData.noMoreUrlToExplore) {
        logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, nextStateData.fetched.size))
      }
      if (nextStateData.assertionPhaseIsFinished) {
        logger.info("%s: no pending assertions" format shortId)
      }
    }
  }

  private final def initialData: RunData = {
    // ask the strategy for the first urls to considerer
    val firstURLs = strategy.seedURLs.toList
    // update the observation state
    val (data, _) = RunData(jobId = job.id, strategy = strategy).withNewUrlsToBeExplored(firstURLs, 0)
    logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
    data
  }

  private final def scheduleNextURLsToFetch(data: RunData): RunData = {
    val (newObservation, explores) = data.takeAtMost(MAX_URL_TO_FETCH)
    explores foreach fetch
    newObservation
  }
  
  /**
   * Fetch one URL
   * 
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: RunData#Explore): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    action match {
      case GET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        http.fetch(url, GET, self)
      }
      case HEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        http.fetch(url, HEAD, self)
      }
      case FetchNothing => {
        logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (shortId, url))
      }
    }
  }

  
  // pure function (no side-effect)
  private final def receiveResponse(fetchResponse: FetchResponse, _data: RunData): (ResourceInfo, RunData) = {
    val data = _data.withCompletedFetch(fetchResponse.url)
    fetchResponse match {
      case OkResponse(url, GET, status, headers, body) => {
        logger.debug("%s: GET <<< %s" format (shortId, url))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val (extractedURLs, newDistance) = headers.mimetype collect {
          case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct -> (distance + 1)
          case "text/css" => URLExtractor.fromCSS(url, body).distinct -> distance /* extract links from CSS here*/
        } getOrElse (List.empty, distance)
        // TODO do something with the newUrls
        val (_newData, newUrls) = data.withNewUrlsToBeExplored(extractedURLs, newDistance)
        if (! newUrls.isEmpty)
          logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, newUrls.size, data.numberOfKnownUrls))
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = GET,
          distancefromSeed = distance,
          result = Fetch(status, headers, extractedURLs))
        (ri, _newData)
      }
      // HEAD
      case OkResponse(url, HEAD, status, headers, _) => {
        logger.debug("%s: HEAD <<< %s" format (shortId, url))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = GET,
          distancefromSeed = distance,
          result = Fetch(status, headers, List.empty))
        (ri, data)
      }
      case KoResponse(url, action, why) => {
        logger.debug("%s: Exception for %s: %s" format (shortId, url, why.getMessage))
        val distance = data.distance.get(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val ri = ResourceInfo(
          url = url,
          jobId = job.id,
          action = GET,
          distancefromSeed = distance,
          result = ResourceInfoError(why.getMessage))
        (ri, data)
      }
    }
  }
  
  // has side-effects
  private final def scheduleAssertion(resourceInfo: ResourceInfo, data: RunData): RunData = {
    val assertors = strategy.assertorsFor(resourceInfo)
    val url = resourceInfo.url
    
    assertors foreach { assertor =>
      val f = Future {
        assertor.assert(url) fold (
          throwable => AssertorFail(
            url = url,
            assertorId = assertor.id,
            jobId = job.id,
            why = throwable.getMessage
          ),
          assertions => Assertions(
            url = url,
            assertorId = assertor.id,
            jobId = job.id,
            assertions = assertions
          )
        )
      }(assertorExecutionContext) recoverWith { case throwable: Throwable =>
        Future {
          AssertorFail(
            url = url,
            assertorId = assertor.id,
            jobId = job.id,
            why = throwable.getMessage)
        }
      }
      f pipeTo self
    }
    
    data.copy(sentAssertorResults = data.sentAssertorResults + assertors.size)
  }
  
  /**
   * To broadcast messages to subscribers.
   */
  private final def broadcast(msg: message.RunUpdate, data: RunData): Unit = {
    data.subscribers foreach ( s => s ! msg )
  }
  
}
