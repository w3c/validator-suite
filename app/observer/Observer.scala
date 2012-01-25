package org.w3.vs.observer

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
//import scala.collection.mutable.{Map, Queue, Seq, Set}
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

/**
 * An Observer is the unity of action that implements
 * the Exploration and Assertion phases
 */
trait Observer {
  // TODO cancelObservation()
  def startExplorationPhase(): Unit
  def stop(): Unit
  def URLs(): Future[Iterable[URL]]
  def assertions(): Future[Assertions]
  // hooks for http
  def sendGETResponse(url: URL, r: GETResponse): Unit
  def sendHEADResponse(url: URL, r: HEADResponse): Unit
  def sendException(url: URL, t: Throwable): Unit
  // foo
  def subscribe(subscriber: ObserverSubscriber): Unit
  def unsubscribe(subscriber: ObserverSubscriber): Unit
  def subscriberOf(subscriber: => Subscriber): ObserverSubscriber
}



class ObserverImpl (
    assertorPicker: AssertorPicker,
    observerId: ObserverId,
    strategy: Strategy) extends Observer {
  
  val logger = Logger.of(classOf[Observer])
  
  import TypedActor.dispatcher
  
  /**
   * Represents the current state of this Observer. The initial state is ExplorationState.
   */
  var observation: Observation = Observation(observerId, strategy)
  
  /**
   * A shorten id for logs readability
   */
  val shortId = observation.shortId

  /**
   * The set of futures waiting for the end of the exploration phase
   */
  var waitingForEndOfExplorationPhase = Set[Promise[Iterable[URL]]]()
  
  /**
   * The set of futures waiting for the end of the assertion phase
   */
  var waitingForEndOfAssertionPhase = Set[Promise[Assertions]]()
  
  /**
   * returns the URLs discovered during the exploration phase
   * Blocks until the exploration phase is done, or returns immediately
   */
  def URLs(): Future[Iterable[URL]] =
    observation.state match {
      case NotYetStarted | ExplorationState => {
        val promise = Promise[Iterable[URL]]()
        waitingForEndOfExplorationPhase += promise
        promise
      }
      case ObservationState | FinishedState => Promise.successful { observation.urls }
      case ErrorState => sys.error("what is a Future with error?")
      case StoppedState => Promise.successful { observation.urls }
    }
  
  /**
   * returns the assertions after the assertion phase
   * Blocks until the assertion phase is done, or returns immediately
   */
  def assertions(): Future[Assertions] =
    observation.state match {
      case NotYetStarted | ExplorationState | ObservationState => {
        val future = Promise[Assertions]()
        waitingForEndOfAssertionPhase += future
        future
      }
      case FinishedState => Promise.successful { observation.assertions }
      case ErrorState => sys.error("what is a Future with error?")
      case StoppedState => Promise.successful { observation.assertions }
    }
  
  /**
   * tests if the exploration phase has to be ended.
   * if it's the case, the assertion phase is started
   */
  def conditionalEndOfExplorationPhase(): Unit = {
//    if (logger.isDebugEnabled) {
//      if (pendingFetches.size < 5) {
//        val fetches = pendingFetches mkString " ; "
//        logger.debug("pending fetches %s" format fetches)
//      } else {
//        logger.debug("pending fetches %d" format pendingFetches.size)
//      }
//    }
    if (observation.explorationPhaseHasEnded) {
      logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, observation.responses.size))
      val urls = observation.urls
      waitingForEndOfExplorationPhase foreach {
        _.success(urls)
      }
      // we don't need pending Futures for crawling anymore
      // make it available for GC
      waitingForEndOfExplorationPhase = null
      startAssertionPhase()
    } else {
      //logger.debug("There are still %d pending fetches" format pendingFetches.size)
    }
  }
  
  /**
   * starts the ExplorationPhase
   * <ul>
   * <li>use the seedURLs to initialize the exploration</li>
   * <li>broadcast the initial state</li>
   * <li>schedule the first fetches</li>
   * </ul>
   */
  def startExplorationPhase(): Unit =
    if (observation.state == NotYetStarted) {
      val firstURLs = strategy.seedURLs.toList
      observation = observation.withFirstURLsToExplore(firstURLs)
      broadcast(URLsToExplore(firstURLs.size))
      logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
      scheduleNextURLsToFetch()
    } else {
      logger.error("you should be in NotYetStarted state, but this is " + observation.state)
    }
  
  def stop(): Unit = {
    observation = observation.stop()
    broadcast(Stopped)
  }

  /**
   * hook to send the result of a GET
   * TODO: explain the logic happening here
   * TODO: move distance into pendingFetches (LinkedHashMap)
   */
  def sendGETResponse(url: URL, r: GETResponse): Unit = {
    if (observation.state != StoppedState) {
      val GETResponse(status, headers, body) = r
      logger.debug("%s:  GET <<< %s" format (shortId, url))
      val distance =
        observation.distanceFor(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
      val extractedURLs = {
        val encoding = "UTF-8"
        val reader = new java.io.StringReader(body)
        // TODO review this clearhash stuff
        html.HtmlParser.parse(url, reader, encoding) map { url: URL => URL.clearHash(url) }
      }
      val urls = observation.filteredExtractedURLs(extractedURLs)
      observation =
        observation
          .withNewResponse(url -> HttpResponse(url, status, headers, urls))
          .withNewUrlsToBeExplored(urls map { (_, distance + 1) })
      if (urls.size > 0) {
        logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, urls.size, observation.numberOfKnownUrls))
      }
      broadcast(FetchedGET(url, status, urls.size))
      scheduleNextURLsToFetch()
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * hook to send the result of a HEAD
   * TODO: explain the logic happening here
   */
  def sendHEADResponse(url: URL, r: HEADResponse): Unit = {
    if (observation.state != StoppedState) {
      val HEADResponse(status, headers) = r
      logger.debug("%s: HEAD <<< %s" format (shortId, url))
      observation = observation.withNewResponse(url -> HttpResponse(url, status, headers, Nil))
      scheduleNextURLsToFetch()
      broadcast(FetchedHEAD(url, r.status))
      conditionalEndOfExplorationPhase()
    }
  }

  /**
   * hook to notice that a problem happened during a fetch (either GET or HEAD)
   * TODO: explain the logic happening here
   */
  def sendException(url: URL, t: Throwable): Unit = {
    if (observation.state != StoppedState) {
      logger.debug("%s: Exception for %s: %s" format (shortId, url, t.getMessage))
      observation = observation.withNewResponse(url -> ErrorResponse(url, t.getMessage))
      scheduleNextURLsToFetch()
      broadcast(FetchedError(url, t.getMessage))
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * fetch one URL
   * depending on the strategy, it does the right fetch, or nothing
   */
  def fetch(explore: Observation#Explore): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    //logger.debug("%s: remaining urls %d" format (shortId, urlsToBeExplored.size))
    action match {
      case FetchGET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        GlobalSystem.http.GET(url, distance, observerId.toString)
      }
      case FetchHEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        GlobalSystem.http.HEAD(url, observerId.toString)
      }
      case FetchNothing => {
        logger.debug("%s: Ignoring %s" format (shortId, url))
      }
    }
  }
  
  // 
  /**
   * schedule the next URLs to fetch
   * the idea is that we don't want to have too many fetches at the same time.
   * this improves the reactivity (if we cancel the exploration) and the fairness (sharing the access to the Fetch module among observers)
   * TODO make 10 a parameter
   */
  def scheduleNextURLsToFetch(): Unit = {
    val (newObservation, explores) = observation.takeAtMost(10)
    //logger.debug(explores.mkString("explores: ", " | ", ""))
    explores foreach fetch
    observation = newObservation
  }

  
  /**
   * starts the assertion phase
   * we currently schedule all the assertion at once as we handle all the assertors
   * TODO this will change with the future Web of Assertors architecture
   */
  def startAssertionPhase(): Unit = {
    logger.info("%s: Starting observation phase" format (shortId))
    val _2XX = observation.responses.toIterable collect { case (url, response: HttpResponse) => (url, response) }
    broadcast(URLsToObserve(_2XX.size))
    val assertions: Assertions = for {
      // TODO Filter only 2xx responses
      (url, response) <- _2XX
      if strategy shouldObserve url
      ctOption = response.headers.contentType
      assertors = assertorPicker.pick(ctOption)
      assertor <- assertors
    } yield {
      try {
        val assertion = assertor.assert(url)
        logger.debug("%s: %s observed by %s" format (shortId, url, assertor.id))
        broadcast(Asserted(url, assertor.id, assertion.errorsNumber, assertion.warningsNumber))
        (url, assertor.id, Right(assertion))
      } catch {
        case t: Throwable => {
          logger.debug("%s: %s got observation error for %s" format (shortId, url, assertor.id))
          broadcast(AssertedError(url, assertor.id, t))
          (url, assertor.id, Left(t))
        }
      }
    }
    
    observation = observation.copy(assertions = assertions)
    
    logger.info("%s: Observation phase done with %d observations" format (shortId, observation.assertions.size))
    waitingForEndOfAssertionPhase foreach { _.success(observation.assertions) }
    // we don't need pending Futures for observations anymore
    // make it available for GC
    waitingForEndOfAssertionPhase = null
    observation = observation.copy(state = FinishedState)
    broadcast(ObservationFinished)
    val context = TypedActor(TypedActor.context)
    subscribers foreach { context.stop(_) }
    subscribers = null
    
  }
  
  
  var subscribers = Set[ObserverSubscriber]()
  
  def subscribe(subscriber: ObserverSubscriber): Unit = {
    subscribers += subscriber
    logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
    subscriber.broadcast(toBroadcast(InitialState))
    logger.debug(toBroadcast(InitialState))
  }
  
  def unsubscribe(subscriber: ObserverSubscriber): Unit = {
    subscribers -= subscriber
    logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
  }

  def toBroadcast(msg: BroadcastMessage): String = msg match {
    case URLsToExplore(nb) => """["NB_EXP", %d]""" format (nb)
    case URLsToObserve(nb) => """["NB_OBS", %d]""" format (nb)
    case FetchedGET(url, httpCode, extractedURLs) => """["GET", %d, "%s", %d]""" format (httpCode, url, extractedURLs)
    case FetchedHEAD(url, httpCode) => """["HEAD", %d, "%s"]""" format (httpCode, url)
    case FetchedError(url, errorMessage) => """["ERR", "%s", "%s"]""" format (errorMessage, url)
    case Asserted(url, assertorId, errors, warnings/*, validatorURL*/) => """["OBS", "%s", "%s", %d, %d]""" format (url, assertorId, errors, warnings)
    case AssertedError(url, assertorId, t) => """["OBS_ERR", "%s"]""" format url
    case NothingToObserve(url) => """["OBS_NO", "%s"]""" format url
    case ObservationFinished => """["OBS_FINISHED"]"""
    case InitialState => {
      val initial = """["OBS_INITIAL", %d, %d, %d, %d]""" format (observation.responses.size, observation.toBeExplored.size, observation.assertions.size, 0)
      import org.w3.vs.model._
      val responsesToBroadcast = observation.responses map {
        // disctinction btw GET and HEAD, links.size??
        case (url, HttpResponse(_, status, _, _)) =>
          toBroadcast(FetchedGET(url, status, 0))
        case (url, ErrorResponse(_, typ)) =>
          toBroadcast(FetchedError(url, typ))
      }
      val assertionsToBroadcast = observation.assertions map {
        case (url, assertorId, Left(t)) =>
          toBroadcast(AssertedError(url, assertorId, t))
        case (url, assertorId, Right(assertion)) =>
          toBroadcast(Asserted(url, assertorId, assertion.errorsNumber, assertion.warningsNumber))
      }
      (List(initial) ++ responsesToBroadcast ++ assertionsToBroadcast) mkString ""
    }
    case Stopped => """["STOPPED"]"""
    
  }
  
  /**
   * to broadcast messages
   */
  def broadcast(msg: BroadcastMessage): Unit = {
    val tb = toBroadcast(msg)
    if (subscribers != null)
      subscribers foreach (_.broadcast(tb))
    else
      logger.debug("%s: no more broadcaster for %s" format (shortId, tb))
  }
  
  def subscriberOf(subscriber: => Subscriber): ObserverSubscriber = {
    TypedActor(TypedActor.context).typedActorOf(
      classOf[ObserverSubscriber],
      subscriber,
      Props(),
      java.util.UUID.randomUUID().toString)
  }

  
  
}
