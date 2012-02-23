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
import scala.collection.mutable.ListMap

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
case class ObserverData(
    strategy: Strategy, // TODO
    distance: Map[URL, Int] = Map.empty,
    // urls that are waiting to be explored
    toBeExplored: List[URL] = List.empty,
    // urls that are being explored
    pending: Set[URL] = Set.empty,
    // urls we have already fetched
    fetched: Set[URL] = Set.empty) {
  
  type Explore = (URL, Int)
  
  def numberOfKnownUrls: Int = distance.size
  
  private def diff(l: Set[URL], r: Set[URL]): Set[URL] = {
    val d1 = l -- r
    val d2 = r -- l
    d1 ++ d2
  }
  
  assert(distance.keySet == fetched ++ pending ++ toBeExplored, diff(distance.keySet, fetched ++ pending ++ toBeExplored).toString)
  assert(toBeExplored.toSet.intersect(pending) == Set.empty)
  assert(pending.intersect(fetched) == Set.empty)
  assert(toBeExplored.toSet.intersect(fetched) == Set.empty)
  
  val logger = play.Logger.of(classOf[ObserverData])
  
  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  final def noMoreUrlToExplore = pending.isEmpty && toBeExplored.isEmpty

  /**
   * An Explore should be ignored if
   * <ul>
   * <li>the strategy says it's not to be fetched</li>
   * <li>it has already been fetched</li>
   * <li>it is already pending</li>
   * <li>it's already scheduled to be fetched</li>
   * </ul>
   */
  private final def shouldIgnore(url: URL, atDistance: Int): Boolean = {
    def notToBeFetched = FetchNothing == strategy.fetch(url, atDistance)
    def alreadyKnown = distance isDefinedAt url
    notToBeFetched || alreadyKnown
  }

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urls: List[URL], atDistance: Int): (ObserverData, List[URL]) = {
    val filteredUrls = urls.filterNot{ url => shouldIgnore(url, atDistance) }.distinct
    val newDistance = distance ++ filteredUrls.map{ url => url -> atDistance }
    val newData = this.copy(
      toBeExplored = toBeExplored ++ filteredUrls,
      distance = newDistance)
    (newData, filteredUrls)
  }
  
  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urlsWithDistance: List[(URL, Int)]): (ObserverData, List[URL]) = {
    // as it's a map, there is no duplicated url :-)
    // also, the ListMap preserves the order of insertion
    
    val map: ListMap[URL, Int] = ListMap.empty
    map ++= urlsWithDistance.filterNot{ case (url, distance) => shouldIgnore(url, distance) }
    val newUrls = map.keys.toList
    val newData = this.copy(
      toBeExplored = toBeExplored ++ newUrls,
      distance = distance ++ map)
    (newData, newUrls)
  }

  val mainAuthority: Authority = strategy.mainAuthority

  /**
   * A consolidated view of all the authorities for the pending urls
   */
  lazy val pendingAuthorities: Set[Authority] = pending map { _.getAuthority }

  /**
   * Returns a couple Observation/Explore.
   * 
   * The Explore  is the first one that could be fetched for the main authority.
   * 
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromMainAuthority: Option[(ObserverData, URL)] = {
    val optUrl = toBeExplored find { _.authority == mainAuthority }
    optUrl map { url =>
      (this.copy(
         pending = pending + url,
         toBeExplored = toBeExplored filterNot { _ == url }),
       url)
    }
  }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched for any authority but the main one.
   * Also, this Explore must be the only one with this Authority.
   * 
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromOtherAuthorities: Option[(ObserverData, URL)] = {
    val pendingToConsiderer =
      toBeExplored.view filterNot { url => url.authority == mainAuthority || (pendingAuthorities contains url.getAuthority) }
    pendingToConsiderer.headOption map { url =>
      (this.copy(
         pending = pending + url,
         toBeExplored = toBeExplored filterNot { _ == url }),
       url)
    }
  }
  
  lazy val mainAuthorityIsBeingFetched = pending exists { _.authority == mainAuthority }
  
  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched, giving priority to the main authority.
   */
  def take: Option[(ObserverData, URL)] = {
    //logger.debug(this.toString)
    if (mainAuthorityIsBeingFetched) {
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
  def takeAtMost(n: Int): (ObserverData, List[Explore]) = {
    var current: ObserverData = this
    var urls: List[URL] = List.empty
    for {
      i <- 1 to (n - pending.size)
      (observation, url) <- current.take
    } {
      current = observation
      urls ::= url
    }
    (current, urls.reverse map { url => url -> distance(url) })
  }
  
  def withCompletedFetch(url: URL): ObserverData = this.copy(
    pending = pending - url,
    fetched = fetched + url
  )
  
}

