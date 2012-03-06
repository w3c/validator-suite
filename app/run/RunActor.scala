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

class RunActor(job: Job)(implicit val configuration: ValidatorSuiteConf) extends Actor with FSM[RunState, RunData] {
  
  import configuration._
  
  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[RunActor])
  
  /**
   * A shorten id for logs readability
   */
  val shortId = job.shortId
  
  final private def takeSnapshot: RunSnapshot = {
    val data = stateData
    import data._
    RunSnapshot(
      jobId = job.id,
      distance = distance,
      toBeExplored = pending.toList ++ toBeExplored,
      fetched = fetched,
      oks = oks,
      errors = errors,
      warnings = warnings)
  }
  
  final private def extractJobData(state: RunState, data: RunData): JobData = JobData(
    status = data.status(state),
    resources = data.numberOfKnownUrls,
    oks = data.oks,
    errors = data.errors,
    warnings = data.warnings)
  
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
  
  implicit def strategy = job.strategy
  
  private final val initialConditions: (RunState, RunData) = store.latestSnapshotFor(job.id) match {
    case Failure(t) => throw t
    case Success(None) => {
      val initialData = replayEventsOn(RunData(strategy), None)
      (Started, initialData)
    }
    case Success(Some(snapshot)) => {
      val resumedData = replayEventsOn(RunData.fromSnapshot(strategy, snapshot), Some(snapshot.createdAt))
      (Started, resumedData)
    }
  }
  
  // at instanciation of this actor
  configuration.system.scheduler.schedule(2 seconds, 5 seconds, self, message.TellTheWorldYouAreAlive)
  startWith(initialConditions._1, initialConditions._2)
  
  whenUnhandled {
    case Event(message.GetStatus, s) => {
      sender ! s.status(stateName)
      stay()
    }
    case Event(message.GetJobData, s) => {
      val jobData = extractJobData(stateName, stateData)
      sender ! jobData
      stay()
    }
    case Event(message.TellTheWorldYouAreAlive, _) => {
      // send to the store a fresher version of this run
      store.putSnapshot(takeSnapshot)
      // tell the subscribers about the current data for this run
      val jobData = extractJobData(stateName, stateData)
      broadcast(message.UpdateData(jobData), stateData)
      logger.info("%s: current data - %s" format (shortId, jobData.toString))
      stay()
    }
  }
  
  onTransition {
    // detect when the status as changed
    case (currentState, nextState) if stateData.status(currentState) != nextStateData.status(nextState) => {
      val jobData = extractJobData(nextState, nextStateData)
      broadcast(message.UpdateData(jobData), nextStateData)
      if (nextStateData.noMoreUrlToExplore) {
        logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, nextStateData.fetched.size))
      }
      if (nextStateData.assertionPhaseIsFinished) {
        logger.info("%s: no pending assertions" format shortId)
      }
    }
  }
  
  when(NotYetStarted) {
    case Event(message.Start, _) => {
      goto(Started) using scheduleNextURLsToFetch(initialData)
    }
  }
  
  when(Started) {
    
    case Event(result: AssertorResult, data) => {
      val data2 = receiveAssertion(result, data)
      stay() using data2
    }
    
    case Event(fetchResponse: FetchResponse, data) => {
      val (resourceInfo, data2) = receiveResponse(fetchResponse, data)
      store.putResourceInfo(resourceInfo)
      broadcast(message.NewResourceInfo(resourceInfo), data2)
      val data3 = scheduleNextURLsToFetch(data2)
      val data4 = scheduleAssertion(resourceInfo, data3)
      stay() using data4
    }
    
    case Event(message.Subscribe(subscriber), data) => {
      logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, data.subscribers.mkString("{", ",", "}")))
      initialState foreach { msg => subscriber ! msg }
      stay() using data.copy(subscribers = data.subscribers + subscriber)
    }
    
    case Event(message.Unsubscribe(subscriber), data) => {
      logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, data.subscribers.mkString("{", ",", "}")))
      stay() using data.copy(subscribers = data.subscribers - subscriber)
    }
    
  }
  
  private final def initialData: RunData = {
    // ask the strategy for the first urls to considerer
    val firstURLs = strategy.seedURLs.toList
    // update the observation state
    val (data, _) = RunData(strategy).withNewUrlsToBeExplored(firstURLs, 0)
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

  
  private final def receiveAssertion(result: AssertorResult, data: RunData): RunData = {
    logger.debug("%s: %s observed by %s" format (shortId, result.url, result.assertorId))
    broadcast(message.NewAssertorResult(result), data)
    store.putAssertorResult(result)
    data.copy(receivedAssertorResults = data.receivedAssertorResults + 1)
  }
  
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
      }(validatorDispatcher) recoverWith { case throwable: Throwable =>
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
  
  // TODO should be a service from the store
  private final def initialState: Iterable[message.RunUpdate] = {
    val resourceInfos = store.listResourceInfos(job.id).either.right.get map message.NewResourceInfo
    val assertions = store.listAssertorResults(job.id).either.right.get map message.NewAssertorResult
    resourceInfos ++ assertions
  }
  
  /**
   * To broadcast messages to subscribers.
   */
  private final def broadcast(msg: message.RunUpdate, data: RunData): Unit = {
    data.subscribers foreach ( s => s ! msg )
  }
  
}
