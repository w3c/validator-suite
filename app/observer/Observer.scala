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
  def stop(): Unit
  // hooks
  def addResponse(fetchResponse: FetchResponse): Unit
  def addAssertion(assertion: Assertion): Unit
  // foo
  def subscribe(subscriber: Subscriber): Unit
  def unsubscribe(subscriber: Subscriber): Unit
  def subscriberOf(subscriber: => Subscriber): Subscriber
}



class ObserverImpl(run: Run)(implicit val configuration: ValidatorSuiteConf) extends Observer {
  
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
  var sentAssertions: Int = 0
  var receivedAssertions: Int = 0
  
  /**
   * The set of subscribers to the events from this Observer
   */
  var subscribers = Set[Subscriber]()
  
  /**
   * The current state of this Observer. The initial state is ExplorationState.
   */
  var data: ObserverData = null//ObserverState(observerId, job)
  
  var state: ObserverState = null
  
  /**
   * A shorten id for logs readability
   */
  val shortId = run.shortId

  startExplorationPhase()
  
//  def job = run.job
  def strategy = run.job.strategy
  
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
  
  def stop(): Unit = {
    state = Stopped
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
  def startExplorationPhase(): Unit = {
    // ask the strategy for the first urls to considerer
    val firstURLs = strategy.seedURLs.toList
    // update the observation state
    val (initialData, _) = ObserverData(strategy).withNewUrlsToBeExplored(firstURLs, 0)
    data = initialData
    logger.info("%s: Starting exploration phase with %d url(s)" format(shortId, firstURLs.size))
    // we can now schedule the first fetches
    scheduleNextURLsToFetch()
  }

  /**
   * Conditional snippet for the end of the Exploration phase
   * if it's the case, the assertion phase is started
   */
  private final def conditionalEndOfExplorationPhase(): Unit = {
    if (data.noMoreUrlToExplore) {
      logger.info("%s: Exploration phase finished. Fetched %d pages" format(shortId, data.fetched.size))
    } else {
      //logger.debug("There are still %d pending fetches" format pendingFetches.size)
    }
  }
  
  private final def scheduleAssertion(resourceInfo: ResourceInfo): Unit = strategy.assertorsFor(resourceInfo) foreach { assertor =>
    sentAssertions += 1
    val url = resourceInfo.url
    Future {
      val result = assertor.assert(url)
      self.addAssertion(Assertion(
        url = url,
        assertorId = assertor.id,
        runId = run.id,
        result = result.fold(t => AssertionError(t.getMessage), r => r)))
    }(validatorDispatcher) recoverWith { case t: Throwable =>
      Future {
        self.addAssertion(Assertion(
          url = url,
          assertorId = assertor.id,
          runId = run.id,
          result = AssertionError(t.getMessage)))
      }
    }
  }
  
  /**
   * Schedule the next URLs to be fetched
   * 
   * The maximum number of pending fetches is decided here.
   */
  private final def scheduleNextURLsToFetch(): Unit = {
    val (newObservation, explores) = data.takeAtMost(MAX_URL_TO_FETCH)
    data = newObservation
    explores foreach fetch
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
  
  /**
   * Hook to send the result of an assertion
   */
  def addAssertion(assertion: Assertion): Unit = {
    receivedAssertions = receivedAssertions + 1
    logger.debug("%s: %s observed by %s" format (shortId, assertion.url, assertion.assertorId))
    broadcast(message.NewAssertion(assertion))
    store.putAssertion(assertion)
    endOfAssertionPhase()
  }

  def addResponse(fetchResponse: FetchResponse): Unit = {
    data = data.withCompletedFetch(fetchResponse.url)
    val (resourceInfo, newData) = fetchResponse match {
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
          runId = run.id,
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
          runId = run.id,
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
          runId = run.id,
          action = GET,
          distancefromSeed = distance,
          result = ResourceInfoError(why.getMessage))
        (ri, data)
      }
    }
    data = newData
    store.putResourceInfo(resourceInfo)
    broadcast(message.NewResourceInfo(resourceInfo))
    scheduleNextURLsToFetch()
    scheduleAssertion(resourceInfo)
    conditionalEndOfExplorationPhase()
  }
  
  /**
   * Conditional snippet of code for the end of the assertion phase.
   * 
   * We know how many assertions are supposed to be received.
   * 
   */
  private final def endOfAssertionPhase(): Unit =
    if (data.toBeExplored.isEmpty && sentAssertions == receivedAssertions) { // @@@ number of assertions so far
      logger.info("%s: Observation phase done with %d observations" format (shortId, receivedAssertions)) // @@@ look into store?
      state = Idle
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
  
  private def initialState: Iterable[message.ObservationUpdate] = {
    val resourceInfos = store.listResourceInfos(run.id).right.get map message.NewResourceInfo
    val assertions = store.listAssertions(run.id).right.get map message.NewAssertion
    resourceInfos ++ assertions
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
