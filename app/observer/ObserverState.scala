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
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration

object ObserverState {
  def getUrl(e: ObserverState#Explore): URL = e._1
  def getDistance(e: ObserverState#Explore) = e._2
}

import ObserverState.{getUrl, getDistance}

/**
 * An Observation represents a coherent state of for an Observer.
 * 
 * The following invariants are enforced:
 * <ul>
 * <li>a URL is either
 *   <ul>
 *   <li>to be explored</li>
 *   <li>being fetch (pending state)</li>
 *   <li>already fetched and has a Response associated to it</li>
 *   </ul>
 * </li>
 * <li>an observation is immutable</li>
 * <li></li>
 * </ul>
 */
case class ObserverState(
  id: ObserverId, 
  strategy: Strategy, 
  phase: ObserverPhase = NotYetStarted, 
  toBeExplored: List[ObserverState#Explore] = List.empty, 
  pendingMainAuthority: Option[ObserverState#Explore] = None, 
  pending: Map[URL, ObserverState#Explore] = Map.empty, 
  responses: Map[URL, Response] = Map.empty, 
  assertions: ObserverState#Assertions = List.empty)
 {
  
  /**
   * This type associated a distance to a particular known url
   * 
   * It's only an alias. You can use utility methods getUrl and getDistance from the companion object
   */
  type Explore = (URL, Int)
  type Assertion = (URL, AssertorId, Either[Throwable, Asserted])
  type Assertions = List[Assertion]
  
  val logger = play.Logger.of(classOf[ObserverState])
  
  override def toString: String =
    """|observation:
       |  id=%s
       |  state=%s,
       |  toBeExplored=%s
       |  pendingMainAuthority=%s
       |  responses=%s
       |  assertions=%s
       |""" format (id.toString, phase.toString, toBeExplored.toString, pendingMainAuthority.toString, responses.keys mkString ", ", assertions.size.toString)
  
  assert(
    allKnownUrls.size == numberOfKnownUrls,
    "each url must be either 1. to be explored 2. pending 3. already explored. (know %d, expected %d)\n%s" format (allKnownUrls.size, numberOfKnownUrls, this.toString))
  
  /**
   * All the know urls through all the relevant data structures
   */
  def allKnownUrls: Set[URL] =
    (pendingMainAuthority map getUrl).toSet ++
      (toBeExplored map getUrl).toSet ++
      pending.keySet ++
      responses.keySet
  
  /**
   * The number of URLs for the entire Observation.
   */
  def numberOfKnownUrls: Int = toBeExplored.size + pendingMainAuthority.size + pending.size + responses.size
  
  val shortId = id.toString.substring(0, 6)

  /**
   * The urls that were already fetched
   */
  def urls: Iterable[URL] = responses.keys

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  def explorationPhaseHasEnded = pendingMainAuthority.isEmpty && pending.isEmpty && toBeExplored.isEmpty

  /**
   * Returns an observation where the seed urls are initialized with a distance at 0
   */
  def withFirstURLsToExplore(urls: List[URL]): ObserverState = {
    val seedUrls = urls.distinct map { (_, 0) }
    this.copy(toBeExplored = seedUrls)
  }

  def stop(): ObserverState = this.copy(phase = Interrupted)

  /**
   * Returns a Observation with the url associated with its Response.
   * 
   * The url is removed from the pending urls.
   */
  def withNewResponse(resp: (URL, Response)): ObserverState = {
    val (url, response) = resp
    
    pendingMainAuthority match {
      case Some((_url, _)) if _url == url => {
        this.copy(
          pendingMainAuthority = None,
          responses = responses + resp)
      }
      case _ => {
        assert(pending.isDefinedAt(url), "%s was not pending" format url.toString)
        this.copy(
          pending = pending - url,
          responses = responses + resp)
      }
    }
  }

  /**
   * The distance for the origin for the given url.
   * 
   * It goes through the urls to be explored *and* the pending urls.
   */
  def distanceFor(url: URL): Option[Int] = {
    def fromPendingMain = pendingMainAuthority map getDistance
    def fromPending = pending.get(url) map getDistance
    def fromToBeExplored = toBeExplored collectFirst { case e if url == getUrl(e) => getDistance(e) }
    fromPendingMain orElse fromPending orElse fromToBeExplored
  }

  /**
   * Higher-order function to filter out Explores with the given url
   */
  private def exploreWith(url: URL): Explore => Boolean = { e: Explore => url == getUrl(e) }

  /**
   * An Explore should be ignored if
   * <ul>
   * <li>the strategy says it's not to be fetched</li>
   * <li>it has already been fetched</li>
   * <li>it is already pending</li>
   * <li>it's already scheduled to be fetched</li>
   * </ul>
   */
  private def shouldIgnore(url: URL, atDistance: Int): Boolean = {
    def notToBeFetched = FetchNothing == strategy.fetch(url, atDistance)
    def alreadyFetched = responses isDefinedAt url
    def alreadyPendingMainAuthority = pendingMainAuthority.isDefined && getUrl(pendingMainAuthority.get) == url
    def alreadyPending = pending isDefinedAt url
    def alreadyScheduled = toBeExplored exists exploreWith(url)
    notToBeFetched || alreadyFetched || alreadyPendingMainAuthority || alreadyPending || alreadyScheduled
  }

  /**
   * Filters the given Explores wrt this Observation and the urls to be ignored
   */
  def filteredExtractedURLs(urls: List[URL], atDistance: Int): List[URL] =
    (urls filterNot { url => shouldIgnore(url, atDistance) } ).distinct

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urls: List[Explore]): ObserverState =
    this.copy(toBeExplored = toBeExplored ++ urls)

  val mainAuthority: Authority = strategy.mainAuthority

  /**
   * A consolidated view of all the pending urls (main authority and others)
   */
  lazy val allPending: Set[URL] = (pendingMainAuthority map getUrl).toSet ++ pending.keySet
  
  /**
   * A consolidated view of all the authorities for the pending urls
   */
  lazy val pendingAuthorities: Set[Authority] = allPending map { _.getAuthority }

  /**
   * Returns a couple Observation/Explore.
   * 
   * The Explore  is the first one that could be fetched for the main authority.
   * 
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromMainAuthority: Option[(ObserverState, Explore)] = {
    val optExplore = toBeExplored find { e => mainAuthority == getUrl(e).getAuthority }
    optExplore map {
      case e @ (url, distance) =>
        (this.copy(
           toBeExplored = toBeExplored filterNot { url == getUrl(_) },
           pendingMainAuthority = optExplore),
         e)
    }
  }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched for any authority but the main one.
   * Also, this Explore must be the only one with this Authority.
   * 
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromOtherAuthorities: Option[(ObserverState, Explore)] = {
    val pendingToConsiderer =
      toBeExplored.view filterNot { case (url, distance) => url.getAuthority == mainAuthority || (pendingAuthorities contains url.getAuthority) }
    pendingToConsiderer.headOption map {
      case e @ (url, distance) =>
        (this.copy(
          toBeExplored = toBeExplored filterNot { url == getUrl(_) },
          pending = pending + (url -> e)),
          e)
    }
  }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched, giving priority to the main authority.
   */
  def take: Option[(ObserverState, Explore)] = {
    //logger.debug(this.toString)
    if (pendingMainAuthority.isDefined) {
      takeFromOtherAuthorities
    } else {
      takeFromMainAuthority orElse takeFromOtherAuthorities
    }
  }

  /**
   * Returns as many Explores as possible to be fetched.
   * 
   * The returned Observation has all the Explores marked as being pending.
   */
  def takeAtMost(n: Int): (ObserverState, List[Explore]) = {
    var current: ObserverState = this
    var explores: List[Explore] = List.empty
    for {
      i <- 1 to (n - allPending.size)
      (observation, e) <- current.take
    } {
      current = observation
      explores ::= e
    }
    (current, explores)
  }
  
  def withAssertion(assertion: Assertion): ObserverState =
    this.copy(assertions = assertion :: assertions)

}

