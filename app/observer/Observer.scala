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
  def assertions(): Future[ObserverState#Assertions]
  // hooks for http
  def sendGETResponse(url: URL, r: GETResponse): Unit
  def sendHEADResponse(url: URL, r: HEADResponse): Unit
  def sendException(url: URL, t: Throwable): Unit
  // foo
  def subscribe(subscriber: Subscriber): Unit
  def unsubscribe(subscriber: Subscriber): Unit
  def subscriberOf(subscriber: => Subscriber): Subscriber
  // bar
  def addAssertion(assertion: ObserverState#Assertion): Unit
}



class ObserverImpl (
    observerId: ObserverId,
    strategy: Strategy)(implicit val configuration: ValidatorSuiteConf) extends Observer {
  
  import configuration._
  
  // TODO is it really what we want? I don't think so
  import TypedActor.dispatcher

  val logger = Logger.of(classOf[Observer])
  
  /**
   * A reference to this observer that can be safely shared with other Akka objects
   * see the warning at http://akka.io/docs/akka/2.0-M3/scala/typed-actors.html#the-tools-of-the-trade
   * for more informations
   */
  val self: Observer = TypedActor.self[Observer]
  
  /**
   */
  val context = TypedActor(TypedActor.context)

  /**
   * A counter for the assertions that are generated
   */
  var assertionCounter: Int = 0
  
  /**
   * The set of subscribers to the events from this Observer
   */
  var subscribers = Set[Subscriber]()
  
  /**
   * The current state of this Observer. The initial state is ExplorationState.
   */
  var state: ObserverState = ObserverState(observerId, strategy)
  
  /**
   * A shorten id for logs readability
   */
  val shortId = state.shortId

  /**
   * The set of Promises waiting for the end of the exploration phase
   */
  var waitingForEndOfExplorationPhase = Set[Promise[Iterable[URL]]]()
  
  /**
   * The set of futures waiting for the end of the assertion phase
   */
  var waitingForEndOfAssertionPhase = Set[Promise[ObserverState#Assertions]]()
  
  /**
   * Creates a subscriber as an Akka-children for this Observer
   * 
   * The id is random
   */
  def subscriberOf(subscriber: => Subscriber): Subscriber = {
    context.typedActorOf(
      classOf[Subscriber],
      subscriber,
      Props(),
      java.util.UUID.randomUUID().toString)
  }
  
  /**
   * Returns the URLs discovered during the exploration phase
   * Blocks until the exploration phase is done, or returns immediately
   */
  def URLs(): Future[Iterable[URL]] =
    state.phase match {
      case NotYetStarted | ExplorationPhase => {
        val promise = Promise[Iterable[URL]]()
        waitingForEndOfExplorationPhase += promise
        promise
      }
      case AssertionPhase | Finished | Interrupted =>
        Promise.successful { state.urls }
      case Error => sys.error("what is a Future with error?")
    }
  
  /**
   * Returns the assertions after the assertion phase
   * Blocks until the assertion phase is done, or returns immediately
   */
  def assertions(): Future[ObserverState#Assertions] =
    state.phase match {
      case NotYetStarted | ExplorationPhase | AssertionPhase => {
        val future = Promise[ObserverState#Assertions]()
        waitingForEndOfAssertionPhase += future
        future
      }
      case Finished | Interrupted => Promise.successful { state.assertions }
      case Error => sys.error("what is a Future with error?")
    }
  
  /**
   * Conditional snippet for the end of the Exploration phase
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
    if (state.explorationPhaseHasEnded) {
      logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, state.responses.size))
      val urls = state.urls
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
   * Starts the assertion phase
   * 
   * we currently schedule all the assertion at once as we handle all the assertors
   * TODO this will change with the future Web of Assertors architecture
   */
  def startAssertionPhase(): Unit = {
    logger.info("%s: Starting observation phase" format (shortId))
    // all the responses with a 200 HTTP response
    val _2XX =
      state.responses.toIterable collect { case (url, response: HttpResponse) => (url, response) }
    for {
      (url, response) <- _2XX                     // iterate over the responses
      if strategy shouldObserve url               // ask the strategy for urls to be observed
      ctOption = response.headers.contentType     // take the content-type (optional) for this response
      assertors = assertorPicker.pick(ctOption)   // and see which assertors are interested
      assertor <- assertors                       // then iterate over these assertors
    } {
      // the call to the assertor to get the assertion
      // it's meant to be executed in a Future (on top of a pool of threads)
      // when the result is known (either a success or an error), the future
      // calls back the Observer asynchronously
      def run = {
        val assertion =
          try {
            (url, assertor.id, Right(assertor.assert(url)))
          } catch {
            case t: Throwable => (url, assertor.id, Left(t))
          }
        self.addAssertion(assertion)
      }
      // at this point, we _know_ that an assertion is expected
      // and will eventually trigger a call to assertionSuccess or assertionFailure
      assertionCounter += 1
      Future(run)(validatorDispatcher)
    }
    // TODO do we need to return the number of urls to observe, or the expected number of assertions?
    broadcast(message.URLsToObserve(_2XX.size))
    logger.debug("expecting %d assertions" format assertionCounter)
  }

  
  /**
   * Starts the ExplorationPhase
   * <ul>
   * <li>use the seedURLs to initialize the exploration</li>
   * <li>broadcast the initial state</li>
   * <li>schedule the first fetches</li>
   * </ul>
   */
  def startExplorationPhase(): Unit =
    if (state.phase == NotYetStarted) {
      // ask the strategy for the first urls to considerer
      val firstURLs = strategy.seedURLs.toList
      // update the observation state
      state = state.withFirstURLsToExplore(firstURLs)
      broadcast(message.URLsToExplore(firstURLs.size))
      logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
      // we can now schedule the first fetches
      scheduleNextURLsToFetch()
    } else {
      logger.error("you should be in NotYetStarted state, but this is " + state.phase)
    }
  
  def stop(): Unit = {
    state = state.stop()
    broadcast(message.Stopped)
  }

  /**
   * Hook to send the result of a GET
   * 
   * The following happens:
   * <ul>
   * <li>the distance for this url is retrieved from the observation</li>
   * <li>depending on the the kind of resource, some urls are extracted</li>
   * <li>and then cleaned before adding them back to the observation (because of the invariants to be respected)</li>
   * <li>the observation is updated with the filtered urls and the new Response</li>
   * <li>an event is broadcasted to the listeners</li>
   * <li>potentially, another fetch is scheduled</li>
   * </ul>
   */
  def sendGETResponse(url: URL, r: GETResponse): Unit = {
    if (state.phase != message.Stopped) {
      val GETResponse(status, headers, body) = r
      logger.debug("%s:  GET <<< %s" format (shortId, url))
      val distance =
        state.distanceFor(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
      // TODO the extraction actually depends on the kind of resource!
      val extractedURLs = {
        val encoding = "UTF-8"
        val reader = new java.io.StringReader(body)
        // TODO review this clearhash stuff
        html.HtmlParser.parse(url, reader, encoding) map { url: URL => URL.clearHash(url) }
      }
      // TODO the distance should be different based on the kind of resource we're looking at
      val potentialExplores = extractedURLs map { _ -> (distance + 1) }
      // it's important to filter/clean the urls before adding them back to the observation
      val explores = state.filteredExtractedURLs(potentialExplores)
      state =
        state
          .withNewResponse(url -> HttpResponse(url, status, headers, extractedURLs.distinct))
          .withNewUrlsToBeExplored(explores)
      if (explores.size > 0) {
        logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, explores.size, state.numberOfKnownUrls))
      }
      broadcast(message.FetchedGET(url, status, explores.size))
      scheduleNextURLsToFetch()
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * Hook to send the result of a HEAD
   * 
   * The logic is the same as in sendGETResponse without the extraction of links
   */
  def sendHEADResponse(url: URL, r: HEADResponse): Unit = {
    if (state.phase != Interrupted) {
      val HEADResponse(status, headers) = r
      logger.debug("%s: HEAD <<< %s" format (shortId, url))
      state = state.withNewResponse(url -> HttpResponse(url, status, headers, Nil))
      scheduleNextURLsToFetch()
      broadcast(message.FetchedHEAD(url, r.status))
      conditionalEndOfExplorationPhase()
    }
  }

  /**
   * Hook to send the result of a failure for a fetch
   * 
   * The logic is the same as in sendHEADResponse
   */
  def sendException(url: URL, t: Throwable): Unit = {
    if (state.phase != Interrupted) {
      logger.debug("%s: Exception for %s: %s" format (shortId, url, t.getMessage))
      state = state.withNewResponse(url -> ErrorResponse(url, t.getMessage))
      scheduleNextURLsToFetch()
      broadcast(message.FetchedError(url, t.getMessage))
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * Fetch one URL
   * 
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: ObserverState#Explore): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    //logger.debug("%s: remaining urls %d" format (shortId, urlsToBeExplored.size))
    action match {
      case FetchGET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        http.GET(url, self)
      }
      case FetchHEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        http.HEAD(url, self)
      }
      case FetchNothing => {
        logger.debug("%s: Ignoring %s. If you're here, remember that you have to remove that url is not pending anymore..." format (shortId, url))
      }
    }
  }
  
  /**
   * Schedule the next URLs to be fetched
   * 
   * The maximum number of pending fetches is decided here.
   */
  private final def scheduleNextURLsToFetch(): Unit = {
    val (newObservation, explores) = state.takeAtMost(MAX_URL_TO_FETCH)
    state = newObservation
    explores foreach fetch
  }
  
  /**
   * Hook to send the result of an assertion
   */
  def addAssertion(assertion: ObserverState#Assertion): Unit = {
    val (url, assertorId, result) = assertion
    logger.debug("%s: %s observed by %s" format (shortId, url, assertorId))
    result match {
      case Right(assertion) =>
        broadcast(message.Asserted(url, assertorId, assertion.errorsNumber, assertion.warningsNumber))
      case Left(t) =>
        broadcast(message.AssertedError(url, assertorId, t))
    }
    val a = (url, assertorId, result)
    state = state.withAssertion(a)
    endOfAssertionPhase()
  }
  
  /**
   * Conditional snippet of code for the end of the assertion phase.
   * 
   * We know how many assertions are supposed to be received.
   * 
   */
  private final def endOfAssertionPhase(): Unit =
    if (assertionCounter == state.assertions.size) {
      logger.info("%s: Observation phase done with %d observations" format (shortId, state.assertions.size))
      waitingForEndOfAssertionPhase foreach { _.success(state.assertions) }
      // we don't need pending Futures for observations anymore
      // make it available for GC
      waitingForEndOfAssertionPhase = null
      state = state.copy(phase = Finished)
      broadcast(message.ObservationFinished)
      subscribers foreach { context.stop(_) }
      subscribers = Set.empty
    }
  
  /* Section for methods related to event listeners */
  
  /**
   * Registers the given subscriber.
   * 
   * The initial state is sent at that time.
   */
  def subscribe(subscriber: Subscriber): Unit = {
    subscribers += subscriber
    logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
    subscriber.broadcast(toBroadcast(message.InitialState))
    logger.debug(toBroadcast(message.InitialState))
  }
  
  /**
   * Unsubscribes the given subscriber.
   */
  def unsubscribe(subscriber: Subscriber): Unit = {
    subscribers -= subscriber
    logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
  }

  /**
   * Utility method to map a BroadcastMessage to a String that can be understood by the client
   */
  private def toBroadcast(msg: message.BroadcastMessage): String = msg match {
    case message.URLsToExplore(nb) => """["NB_EXP", %d]""" format (nb)
    case message.URLsToObserve(nb) => """["NB_OBS", %d]""" format (nb)
    case message.FetchedGET(url, httpCode, extractedURLs) => """["GET", %d, "%s", %d]""" format (httpCode, url, extractedURLs)
    case message.FetchedHEAD(url, httpCode) => """["HEAD", %d, "%s"]""" format (httpCode, url)
    case message.FetchedError(url, errorMessage) => """["ERR", "%s", "%s"]""" format (errorMessage, url)
    case message.Asserted(url, assertorId, errors, warnings/*, validatorURL*/) => """["OBS", "%s", "%s", %d, %d]""" format (url, assertorId, errors, warnings)
    case message.AssertedError(url, assertorId, t) => """["OBS_ERR", "%s"]""" format url
    case message.NothingToObserve(url) => """["OBS_NO", "%s"]""" format url
    case message.ObservationFinished => """["OBS_FINISHED"]"""
    case message.InitialState => {
      val initial = """["OBS_INITIAL", %d, %d, %d, %d]""" format (state.responses.size, state.toBeExplored.size, state.assertions.size, 0)
      import org.w3.vs.model._
      val responsesToBroadcast = state.responses map {
        // disctinction btw GET and HEAD, links.size??
        case (url, HttpResponse(_, status, _, _)) =>
          toBroadcast(message.FetchedGET(url, status, 0))
        case (url, ErrorResponse(_, typ)) =>
          toBroadcast(message.FetchedError(url, typ))
      }
      val assertionsToBroadcast = state.assertions map {
        case (url, assertorId, Left(t)) =>
          toBroadcast(message.AssertedError(url, assertorId, t))
        case (url, assertorId, Right(assertion)) =>
          toBroadcast(message.Asserted(url, assertorId, assertion.errorsNumber, assertion.warningsNumber))
      }
      (List(initial) ++ responsesToBroadcast ++ assertionsToBroadcast) mkString ""
    }
    case message.Stopped => """["STOPPED"]"""
    
  }
  
  /**
   * To broadcast messages to subscribers.
   */
  private def broadcast(msg: message.BroadcastMessage): Unit = {
    val tb = toBroadcast(msg)
    if (subscribers != null)
      subscribers foreach (_.broadcast(tb))
    else
      logger.debug("%s: no more broadcaster for %s" format (shortId, tb))
  }
  
}
