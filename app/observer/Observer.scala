package org.w3.vs.observer

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import scala.collection.mutable.{Map, Queue, Seq, Set}
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

object Observer {
  
  val registry = Actor.registry
  
  def byObserverId(observerId: ObserverId): Option[Observer] =
    registry.typedActorsFor(observerId.toString).headOption map { (_.asInstanceOf[Observer]) }

  def newCompletableFuture[T]() = new DefaultCompletableFuture[T](30.seconds.toMillis)

  lazy val http = Http.getInstance()

  def newObserver(
      observerId: ObserverId,
      strategy: Strategy,
      assertorPicker: AssertorPicker = SimpleAssertorPicker,
      timeout: Duration = 10.second) =
    TypedActor.newInstance(
      classOf[Observer],
      new ObserverImpl(http, assertorPicker, observerId, strategy) with ObserverSubscribers,
      timeout.toMillis)

}

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
  // hooks for Assertor
  def sendAssertion(
      url: URL, 
      assertorId: AssertorId, 
      assertion: Assertion, 
      expectedNumberOfAssertors: Int): Unit
  def sendAssertionError(
      url: URL, 
      assertorId: AssertorId, 
      t: Throwable, 
      expectedNumberOfAssertors: Int): Unit
  def noAssertion(url: URL): Unit
  //
  def subscribe(subscriber: ObserverSubscriber): Unit
  def unsubscribe(subscriber: ObserverSubscriber): Unit
}

abstract class ObserverImpl (
    http: Http,
    assertorPicker: AssertorPicker,
    observerId: ObserverId,
    strategy: Strategy) extends TypedActor with Observer {
  
  val logger = Logger.of(classOf[Observer])
  
  self.id = observerId.uuid.toString
  
  /**
   * A shorten id for logs readability
   */
  val shortId = self.id.substring(0,6)

  /**
   * Represents the current state of this Observer. The initial state is ExplorationState.
   */
  var state: ObserverState = null
  
  // follows the data structure for http responses and assertions
  
  val responses = Map[URL, Response]()
  
  var _assertions = List[(URL, AssertorId, Either[Throwable, Assertion])]()
  
  val urlsToBeExplored = LinkedHashMap[URL, (Int, FetchAction)]()
  
  val pendingFetches = LinkedHashMap[URL, Int]()
  
  val pendingAssertions = Map[URL, Int]()
  
  /**
   * The set of futures waiting for the end of the exploration phase
   */
  var waitingForEndOfExplorationPhase = Set[CompletableFuture[Iterable[URL]]]()
  
  /**
   * The set of futures waiting for the end of the assertion phase
   */
  var waitingForEndOfAssertionPhase = Set[CompletableFuture[Assertions]]()
  
  /**
   * The main authority of the current strategy.
   * TODO: should be given by the strategy directly
   */
  val mainAuthority: Authority = strategy.seedURLs.headOption map {_.authority} getOrElse sys.error("No seed url in strategy")
  
  /**
   * a proxied assertor that can receive assertion commands
   * and that replies back to this observer asynchronously
   */
  lazy val assertor = FromHttpResponseAssertor.newInstance(observerId, assertorPicker)
  
  /**
   * hook to broadcast messages
   * the sub-class or mixin must deal with the subscribers
   */
  def broadcast(msg: BroadcastMessage): Unit 

  /**
   * returns the URLs discovered during the exploration phase
   * Blocks until the exploration phase is done, or returns immediately
   */
  def URLs(): Future[Iterable[URL]] =
    state match {
      case ExplorationState => {
        val future = Observer.newCompletableFuture[Iterable[URL]]()
        waitingForEndOfExplorationPhase += future
        future
      }
      case ObservationState | FinishedState => future { responses.keys }
      case ErrorState => sys.error("what is a Future with error?")
    }
  
  /**
   * returns the assertions after the assertion phase
   * Blocks until the assertion phase is done, or returns immediately
   */
  def assertions(): Future[Assertions] =
    state match {
      case ExplorationState | ObservationState => {
        val future = Observer.newCompletableFuture[Assertions]()
        waitingForEndOfAssertionPhase += future
        future
      }
      case FinishedState => future { _assertions }
      case ErrorState => sys.error("what is a Future with error?")
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
    if (pendingFetches.isEmpty && urlsToBeExplored.isEmpty) {
      logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, responses.size))
      waitingForEndOfExplorationPhase foreach { _.completeWithResult(responses.keys) }
      // we don't need pending Futures for crawling anymore
      // make it available for GC
      waitingForEndOfExplorationPhase = null
      startAssertionPhase()
    } else {
      //logger.debug("There are still %d pending fetches" format pendingFetches.size)
    }
  }
  
  /**
   * tests if the assertion phase has to be ended.
   * if it's the case, the observer enters the FinishedState
   */
  def conditionalEndOfAssertionPhase(): Boolean = {
    val b = pendingAssertions.isEmpty
    if (b) {
      logger.info("%s: Observation phase done with %d observations" format (shortId, _assertions.size))
      waitingForEndOfAssertionPhase foreach { _.completeWithResult(_assertions) }
      // we don't need pending Futures for observations anymore
      // make it available for GC
      waitingForEndOfAssertionPhase = null
      state = FinishedState
      broadcast(ObservationFinished)
    }
    b
  }
  
  /**
   * starts the ExplorationPhase
   * <ul>
   * <li>use the seedURLs to initialize the exploration</li>
   * <li>broadcast the initial state</li>
   * <li>schedule the first fetches</li>
   * </ul>
   */
  def startExplorationPhase(): Unit = if (state == null) {
    state = ExplorationState
    val firstURLs = strategy.seedURLs
    urlsToBeExplored ++= firstURLs map { (_, (0, FetchGET)) }
    broadcast(URLsToExplore(firstURLs.size))
    logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
    scheduleNextURLsToFetch()
  }
  
  def stop(): Unit = {
    state = StoppedState
    urlsToBeExplored.clear()
    pendingAssertions.clear()
    pendingFetches.clear()
    broadcast(Stopped)
  }

  /**
   * hook to send the result of a GET
   * TODO: explain the logic happening here
   * TODO: move distance into pendingFetches (LinkedHashMap)
   */
  def sendGETResponse(url: URL, r: GETResponse): Unit = {
    if (state != StoppedState) {
      val GETResponse(status, headers, body) = r
      logger.debug("%s:  GET <<< %s" format (shortId, url))
      val distance: Int = pendingFetches remove url getOrElse sys.error("Broken assumption: %s wasn't in pendingFetches" format url)
      val encoding = "UTF-8"
      val reader = new java.io.StringReader(body)
      val extractedURLs = html.HtmlParser.parse(url, reader, encoding) map { url: URL => URL.clearHash(url) }
      responses +=
        (url -> HttpResponse(url, status, headers, extractedURLs))
      def ignore(url: URL): Boolean = {
        def isPending = pendingFetches contains url
        def isProcessed = responses isDefinedAt url
        def isScheduled = urlsToBeExplored contains url
        isPending || isProcessed || isScheduled
      }
      val urls = extractedURLs.distinct flatMap {
        url => {
          lazy val action = strategy.fetch(url, distance+1)
          if (ignore(url) || action == FetchNothing)
            None
          else
            Some(url -> (distance + 1, action))
        }
      }
      urlsToBeExplored ++= urls
      if (urls.size > 0) {
        val total = responses.size + urlsToBeExplored.size + pendingFetches.size
        logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, urls.size, total))
      }
      broadcast(FetchedGET(url, r.status, urls.size))
      scheduleNextURLsToFetch()
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * hook to send the result of a HEAD
   * TODO: explain the logic happening here
   */
  def sendHEADResponse(url: URL, r: HEADResponse): Unit = {
    if (state != StoppedState) {
      val HEADResponse(status, headers) = r
      logger.debug("%s: HEAD <<< %s" format (shortId, url))
      pendingFetches -= url
      scheduleNextURLsToFetch()
      responses +=
        (url -> HttpResponse(url, status, headers, Nil))
      broadcast(FetchedHEAD(url, r.status))
      conditionalEndOfExplorationPhase()
    }
  }

  /**
   * hook to notice that a problem happened during a fetch (either GET or HEAD)
   * TODO: explain the logic happening here
   */
  def sendException(url: URL, t: Throwable): Unit = {
    if (state != StoppedState) {
      logger.debug("%s: Exception for %s: %s" format (shortId, url, t.getMessage))
      pendingFetches -= url
      scheduleNextURLsToFetch()
      responses += (url -> ErrorResponse(url, t.getMessage))
      broadcast(FetchedError(url, t.getMessage))
      conditionalEndOfExplorationPhase()
    }
  }
  
  /**
   * explores one URL
   * depending on the strategy, it does the right fetch, or nothing
   */
  def explore(url: URL): Unit = {
    //logger.debug("%s: remaining urls %d" format (shortId, urlsToBeExplored.size))
    val (distance, action) =
      urlsToBeExplored remove url getOrElse sys.error("Broken assumption: %s wasn't in urlsToBeExplored" format url)
    action match {
      case FetchGET => {
        logger.debug("%s: GET >>> %s" format (shortId, url))
        pendingFetches += url -> distance
        http.GET(url, distance, self.id)
      }
      case FetchHEAD => {
        logger.debug("%s: HEAD >>> %s" format (shortId, url))
        pendingFetches += url -> distance
        http.HEAD(url, self.id)
      }
      case FetchNothing => {
        logger.error("%s: Ignoring %s" format (shortId, url))
      }
    }
  }
  
  /**
   * returns the next best URL to fetch
   * best is defined in this order
   * <ul>
   * <li>a URL whose authority is the same as the main authority, if there is no pending fetch with this authority</li>
   * <li>a URL such that there is no pending fetch with the same authority</li>
   * <li>Nothing</li>
   * </ul>
   * this method has no side-effect
   * TODO: two pendingFetches ? One for main authority and one for the rest
   */
  def nextURLToFetch(): Option[URL] = {
    def noPendingFetchFor(authority: Authority) = ! pendingFetches.exists{case (url, _) => url.getAuthority == authority}
    def mainAuthorityURL = urlsToBeExplored.collectFirst{case (url, _) if url.getAuthority == mainAuthority && noPendingFetchFor(mainAuthority) => url }
    def otherURL = urlsToBeExplored.collectFirst{case (url, _) if noPendingFetchFor(url.getAuthority) => url }
    mainAuthorityURL orElse otherURL
  }
  
  // 
  /**
   * schedule the next URLs to fetch
   * the idea is that we don't want to have too many fetches at the same time.
   * this improves the reactivity (if we cancel the exploration) and the fairness (sharing the access to the Fetch module among observers)
   * TODO make 10 a parameter
   */
  def scheduleNextURLsToFetch(): Unit = {
    for {
      _ <- 1 to (10 - pendingFetches.size)
      url <- nextURLToFetch
    } {
      // there is a side-effect here, so nextURLToFetch will be different in the next iteration
      explore(url)
    }
  }
  
  /**
   * starts the assertion phase
   * we currently schedule all the assertion at once as we handle all the assertors
   * TODO this will change with the future Web of Assertors architecture
   */
  def startAssertionPhase(): Unit = {
    logger.info("%s: Starting observation phase" format (shortId))
    state = ObservationState
    for {
      // TODO Filter only 2xx responses
      (url, response: HttpResponse) <- responses
    } {
      if (strategy shouldObserve url) {
        assertor.assert(response)
        pendingAssertions += (url -> 0)
      }
    }
    broadcast(URLsToObserve(pendingAssertions.size))
  }
  
  /**
   * hook to send the result of an assertion
   * TODO explain the logic
   */
  def sendAssertion(
      url: URL, 
      assertorId: AssertorId, 
      assertion: Assertion, 
      expectedNumberOfAssertions: Int): Unit = {
    logger.debug("%s: %s observed by %s (1/%d)" format (shortId, url, assertorId, expectedNumberOfAssertions))
    val currentNumberOfAssertions = pendingAssertions(url) + 1
    
    if (expectedNumberOfAssertions == currentNumberOfAssertions) {
      pendingAssertions -= url
    } else {
      pendingAssertions += (url -> currentNumberOfAssertions)
    }
    
    _assertions ::= (url, assertorId, Right(assertion))
    
    broadcast(
        Asserted(
            url,
            assertorId,
            assertion.errorsNumber,
            assertion.warningsNumber))
    conditionalEndOfAssertionPhase()
  }
  
  /**
   * hook to notice the failure of an assertion
   */
  def sendAssertionError(
      url: URL, 
      assertorId: AssertorId, 
      t: Throwable, 
      expectedNumberOfAssertions: Int): Unit = {
    logger.debug("%s: %s got observation error for %s (1/%d)" format (shortId, url, assertorId, expectedNumberOfAssertions))
    val currentObservations = pendingAssertions(url) + 1
    
    if (expectedNumberOfAssertions == currentObservations) {
      pendingAssertions -= url
    } else {
      pendingAssertions += (url -> currentObservations)
    }
    
    val newObservationError = (assertorId -> Left(t))

    _assertions ::= (url, assertorId, Left(t))

    broadcast(AssertedError(url, assertorId, t))
    conditionalEndOfAssertionPhase()
  }
  
  /**
   * hook to notice that no assertor was found for this URL
   */
  def noAssertion(url: URL): Unit = {
    logger.debug("%s: no observation for %s" format (shortId, url))
    pendingAssertions -= url
    
    // if no observation, we just associate an empty map to this url
    // so that we know we did ask the ObserverPicker
    // observations += (url -> Map())
    
    broadcast(NothingToObserve(url))
    conditionalEndOfAssertionPhase()
  }
  
}
