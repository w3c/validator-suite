package org.w3.vs.observer

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


class Observer(job: Job)(implicit val configuration: ValidatorSuiteConf) extends Actor with FSM[ObserverState, ObserverData] {
  
  import configuration._
  
  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[Observer])
  
  /**
   * A shorten id for logs readability
   */
  val shortId = job.shortId
  
  implicit def strategy = job.strategy
  
  startWith(Running, scheduleNextURLsToFetch(startExploration))
  
  def startExploration: ObserverData = {
    // ask the strategy for the first urls to considerer
    val firstURLs = strategy.seedURLs.toList
    // update the observation state
    val (initialData, _) = ObserverData(strategy).withNewUrlsToBeExplored(firstURLs, 0)
    logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
    initialData
  }

  def scheduleNextURLsToFetch(data: ObserverData): ObserverData = {
    val (newObservation, explores) = data.takeAtMost(MAX_URL_TO_FETCH)
    explores foreach fetch
    newObservation
  }
  
    /**
   * Fetch one URL
   * 
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: ObserverData#Explore): Unit = {
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
  
//  when(Idle) {
//    case Event(_, _) => logger.debug("we're done!!!")
//  }
  
  when(Running) {
    case Event(assertion: Assertion, data) => {
      val data2 = receiveAssertion(assertion, data)
      if (data2.assertionPhaseIsFinished) {
        val data3 = endAssertionPhase(data: ObserverData)
        stop(FSM.Normal)
      }
      else {
        goto(Running) using data2
      }
    }
    case Event(fetchResponse: FetchResponse, data) => {
      val (resourceInfo, data2) = receiveResponse(fetchResponse, data)
      store.putResourceInfo(resourceInfo)
      broadcast(message.NewResourceInfo(resourceInfo), data2)
      val data3 = scheduleNextURLsToFetch(data2)
      val data4 = scheduleAssertion(resourceInfo, data3)
      if (data4.noMoreUrlToExplore) {
        logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, data4.fetched.size))
      } else {
        //logger.debug("There are still %d pending fetches" format pendingFetches.size)
      }
      goto(Running) using data4
    }
    case Event(message.Subscribe(subscriber), data) => {
      logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, data.subscribers.mkString("{", ",", "}")))
      initialState foreach { msg => subscriber ! msg }
      goto(Running) using data.copy(subscribers = data.subscribers + subscriber)
    }
    case Event(message.Unsubscribe(subscriber), data) => {
      logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, data.subscribers.mkString("{", ",", "}")))
      goto(Running) using data.copy(subscribers = data.subscribers - subscriber)
    }
  }
  
  private final def receiveAssertion(assertion: Assertion, data: ObserverData): ObserverData = {
    logger.debug("%s: %s observed by %s" format (shortId, assertion.url, assertion.assertorId))
    broadcast(message.NewAssertion(assertion), data)
    store.putAssertion(assertion)
    data.copy(receivedAssertions = data.receivedAssertions + 1)
  }
  
  private final def endAssertionPhase(data: ObserverData): ObserverData = {
    logger.info("%s: Observation phase done with %d observations" format (shortId, data.receivedAssertions)) // @@@ look into store?
    broadcast(message.Done, data)
    val ctx = TypedActor(context)
    data.subscribers foreach { s => ctx.stop(s) }
    data.copy(subscribers = Set.empty)
  }
  
  private final def receiveResponse(fetchResponse: FetchResponse, _data: ObserverData): (ResourceInfo, ObserverData) = {
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
  
  
  private final def scheduleAssertion(resourceInfo: ResourceInfo, data: ObserverData): ObserverData = {
    val assertors = strategy.assertorsFor(resourceInfo)
    val url = resourceInfo.url
    
    assertors foreach { assertor =>
      val f = Future {
        val result = assertor.assert(url)
        Assertion(
          url = url,
          assertorId = assertor.id,
          jobId = job.id,
          // TODO check the 401 here?
          result = result.fold(t => AssertionError(t.getMessage), r => r))
      }(validatorDispatcher) recoverWith { case t: Throwable =>
        Future {
          Assertion(
            url = url,
            assertorId = assertor.id,
            jobId = job.id,
            result = AssertionError(t.getMessage))
        }
      }
      f pipeTo self
    }
    
    data.copy(sentAssertions = data.sentAssertions + assertors.size)
  }
  

  // TODO should be a service from the store
  private final def initialState: Iterable[message.ObservationUpdate] = {
    val resourceInfos = store.listResourceInfos(job.id).either.right.get map message.NewResourceInfo
    val assertions = store.listAssertions(job.id).either.right.get map message.NewAssertion
    resourceInfos ++ assertions
  }
  
  /**
   * To broadcast messages to subscribers.
   */
  private final def broadcast(msg: message.ObservationUpdate, data: ObserverData): Unit = {
    data.subscribers foreach ( s => s ! msg )
  }
  
}
