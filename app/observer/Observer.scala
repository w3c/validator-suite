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
import org.w3.util.Headers.wrapHeaders

/**
 * An Observer is the unity of action that implements
 * the Exploration and Assertion phases
 */
trait Observer {
  // TODO cancelObservation()
  def stop(): Unit
  def URLs(): Future[Iterable[URL]]
  def assertions(): Future[ObserverState#Assertions]
  // hooks
  def addResponse(fetchResponse: FetchResponse): Unit
  def addAssertion(assertion: Assertion): Unit
  // foo
  def subscribe(subscriber: Subscriber): Unit
  def unsubscribe(subscriber: Subscriber): Unit
  def subscriberOf(subscriber: => Subscriber): Subscriber
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
  
  startExplorationPhase()
  
  /**
   * Creates a subscriber as an Akka-children for this Observer
   * 
   * The id is random
   */
  def subscriberOf(subscriber: => Subscriber): Subscriber = {
    context.typedActorOf(
      TypedProps(
        classOf[Subscriber],
        subscriber),
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
  
  def stop(): Unit = {
    state = state.stop()
    broadcast(message.Stopped)
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
      broadcast(message.NewURLsToExplore(firstURLs))
      logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
      // we can now schedule the first fetches
      scheduleNextURLsToFetch()
    } else {
      logger.error("you should be in NotYetStarted state, but this is " + state.phase)
    }

  /**
   * Conditional snippet for the end of the Exploration phase
   * if it's the case, the assertion phase is started
   */
  private final def conditionalEndOfExplorationPhase(): Unit = {
    if (state.noMoreUrlToExplore) {
      logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, state.responses.size))
      val urls = state.urls
      waitingForEndOfExplorationPhase foreach {
        _.success(urls)
      }
      // we don't need pending Futures for crawling anymore
      // make it available for GC
      waitingForEndOfExplorationPhase = null
    } else {
      //logger.debug("There are still %d pending fetches" format pendingFetches.size)
    }
  }
  
  private final def scheduleAssertion(response: Response): Unit = response match {
    case HttpResponse(url, _, 200, headers, _) =>
      if (strategy.shouldObserve(url)) {
        headers.mimetype foreach {
          case "text/html" | "application/xhtml+xml" => {
            assertionCounter += 1
            Future {
              val assertion = HTMLValidator.assert(url)
              self.addAssertion(assertion)
            }(validatorDispatcher) recover { // TODO when Play updates to newer version of Akka, use recoverWith with other dispatcher
              case t: Throwable => {
                self.addAssertion(Assertion(url, HTMLValidator.id, AssertionError(t)))
              }
            }
          }
          case "text/css" => {
            assertionCounter += 1
            Future {
              val assertion = CSSValidator.assert(url)
              self.addAssertion(assertion)
            }(validatorDispatcher) recover { // TODO when Play updates to newer version of Akka, use recoverWith with other dispatcher
              case t: Throwable => {
                self.addAssertion(Assertion(url, CSSValidator.id, AssertionError(t)))
              }
            }
          }
          case mimetype => logger.debug("no known assertor for %s" format mimetype)
        }
      }
    case _ => ()
  }
  
  private final def extractURLsFromHtml(url: URL, body: String): List[URL] = {
    val encoding = "UTF-8"
    val reader = new java.io.StringReader(body)
    html.HtmlParser.parse(url, reader, encoding) map { url: URL => URL.clearHash(url) }
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
   * Fetch one URL
   * 
   * The kind of fetch is decided by the strategy (can be no fetch)
   */
  private final def fetch(explore: ObserverState#Explore): Unit = {
    val (url, distance) = explore
    val action = strategy.fetch(url, distance)
    //logger.debug("%s: remaining urls %d" format (shortId, urlsToBeExplored.size))
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
  
  /**
   * Hook to send the result of an assertion
   */
  def addAssertion(assertion: Assertion): Unit = {
    val Assertion(url, assertorId, _) = assertion
    logger.debug("%s: %s observed by %s" format (shortId, url, assertorId))
    broadcast(message.NewAssertion(assertion))
    state = state.withAssertion(assertion)
    endOfAssertionPhase()
  }

  def addResponse(fetchResponse: FetchResponse): Unit = if (state.phase != message.Stopped) {
    fetchResponse match {
      case OkResponse(url, GET, status, headers, body) => {
        logger.debug("%s: GET <<< %s" format (shortId, url))
        val distance = state.distanceFor(url) getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
        val extractedURLs = headers.mimetype map {
          case "text/html" | "application/xhtml+xml" => extractURLsFromHtml(url, body)
          case "text/css" => List.empty /* extract links from CSS here*/
          case _ => List.empty
        } getOrElse List.empty
        val newUrls = state.filteredExtractedURLs(extractedURLs, distance + 1)
        if (! newUrls.isEmpty)
          logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, newUrls.size, state.numberOfKnownUrls))
        val newResponse = HttpResponse(url, GET, status, headers, extractedURLs.distinct)
        val newExplores = newUrls map { (_, distance + 1) }
        state = state.withNewResponse(url -> newResponse).withNewUrlsToBeExplored(newExplores)
        broadcast(message.NewResponse(newResponse))
        broadcast(message.NewURLsToExplore(newUrls))
        scheduleAssertion(newResponse)
        scheduleNextURLsToFetch()
        conditionalEndOfExplorationPhase()
      }
      // HEAD
      case OkResponse(url, HEAD, status, headers, _) => {
        logger.debug("%s: HEAD <<< %s" format (shortId, url))
        val response = HttpResponse(url, HEAD, status, headers, Nil)
        state = state.withNewResponse(url -> response)
        scheduleNextURLsToFetch()
        broadcast(message.NewResponse(response))
        conditionalEndOfExplorationPhase()
      }
      case KoResponse(url, action, why) => {
        logger.debug("%s: Exception for %s: %s" format (shortId, url, why.getMessage))
        state = state.withNewResponse(url -> ErrorResponse(url, why.getMessage))
        scheduleNextURLsToFetch()
        broadcast(message.NewResponse(ErrorResponse(url, why.getMessage)))
        conditionalEndOfExplorationPhase()
      }
    }
  }
  
  /**
   * Conditional snippet of code for the end of the assertion phase.
   * 
   * We know how many assertions are supposed to be received.
   * 
   */
  private final def endOfAssertionPhase(): Unit =
    if (state.toBeExplored.isEmpty && assertionCounter == state.assertions.size) {
      logger.info("%s: Observation phase done with %d observations" format (shortId, state.assertions.size))
      waitingForEndOfAssertionPhase foreach { _.success(state.assertions) }
      // we don't need pending Futures for observations anymore
      // make it available for GC
      waitingForEndOfAssertionPhase = null
      state = state.copy(phase = Finished)
      broadcast(message.Done)
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
    subscriber.broadcast(initialState)
  }
  
  private def initialState: message.ObservationSnapshot = {
    val responsesToBroadcast = state.responses map { case (_, response) => message.NewResponse(response) }
    val assertionsToBroadcast = state.assertions map { a => message.NewAssertion(a) }
    val messages = responsesToBroadcast ++ assertionsToBroadcast
    message.ObservationSnapshot(state.responses.size, state.toBeExplored.size, state.assertions.size, messages)
  }
  
  /**
   * Unsubscribes the given subscriber.
   */
  def unsubscribe(subscriber: Subscriber): Unit = {
    subscribers -= subscriber
    logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
  }

  
  /**
   * To broadcast messages to subscribers.
   */
  private def broadcast(msg: message.ObservationUpdate): Unit = {
    if (subscribers != null)
      subscribers foreach (_.broadcast(msg))
    else
      logger.debug("%s: no more broadcaster for %s" format (shortId, msg.toString))
  }
  
}
