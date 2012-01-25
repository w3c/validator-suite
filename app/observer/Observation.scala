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
import System.{ currentTimeMillis => now }
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration

object Observation {
  def getUrl(e: Observation#Explore): URL = e._1
  def getDistance(e: Observation#Explore) = e._2
}

import Observation.{ getUrl, getDistance }

case class Observation(
  id: ObserverId,
  strategy: Strategy,
  state: ObserverState = NotYetStarted,
  toBeExplored: List[Observation#Explore] = List.empty,
  pendingMainAuthority: Option[Observation#Explore] = None,
  pending: Map[URL, Observation#Explore] = Map.empty,
  responses: Map[URL, Response] = Map.empty,
  assertions: Assertions = List.empty) {
  
  override def toString: String =
    """|observation:
       |  id=%s
       |  state=%s,
       |  toBeExplored=%s
       |  pendingMainAuthority=%s
       |  responses=%s
       |  assertions=%s""" format (id.toString, state.toString, toBeExplored.toString, pendingMainAuthority.toString, responses.keys mkString ", ", assertions.size.toString)

  type Explore = (URL, Int)

  def allKnownUrls: Set[URL] =
    (pendingMainAuthority map getUrl).toSet ++
      (toBeExplored map getUrl).toSet ++
      pending.keySet ++
      responses.keySet

  def numberOfKnownUrls: Int = toBeExplored.size + pendingMainAuthority.size + pending.size + responses.size

//  assert(
//    allKnownUrls.size == numberOfKnownUrls,
//    """|each url must be either 1. to be explored 2. pending 3. already explored
//       |  observation: %s
//       |  (%d) known urls: %s
//       |  numberOfKnownUrls: %d""" format ("" /*this.toString*/ , allKnownUrls.size, "" /*allKnownUrls.toString*/ , numberOfKnownUrls))

//  assert(
//    allKnownUrls.size == numberOfKnownUrls,
//    """|each url must be either 1. to be explored 2. pending 3. already explored
//       |  observation: %s
//       |  (%d) known urls: %s
//       |  numberOfKnownUrls: %d""" format (this.toString , allKnownUrls.size, allKnownUrls.toString , numberOfKnownUrls))

  assert(
    allKnownUrls.size == numberOfKnownUrls,
    "each url must be either 1. to be explored 2. pending 3. already explored. (know %d, expected %d)\n%s" format (allKnownUrls.size, numberOfKnownUrls, this.toString))
  
  val shortId = id.toString.substring(0, 6)

  def urls: Iterable[URL] = responses.keys

  def explorationPhaseHasEnded = pendingMainAuthority.isEmpty && pending.isEmpty && toBeExplored.isEmpty

  def withFirstURLsToExplore(urls: List[URL]): Observation = {
    val seedUrls = urls.distinct map { (_, 0) }
    this.copy(toBeExplored = seedUrls)
  }

  def stop(): Observation = this.copy(state = StoppedState)

  def withNewResponse(resp: (URL, Response)) = {
    val (url, response) = resp
    if (pendingMainAuthority.isDefined)
      this.copy(
        pendingMainAuthority = None,
        responses = responses + resp)
    else
      this.copy(
        pending = pending - url,
        responses = responses + resp)
  }

  /**
   * the distance for the origin for the given url
   * search first in the pendingUrls then in urlsToBeExplored
   */
  def distanceFor(url: URL): Option[Int] = {
    def fromPendingMain = pendingMainAuthority map getDistance
    def fromPending = pending.get(url) map getDistance
    def fromToBeExplored = toBeExplored collectFirst { case e if url == getUrl(e) => getDistance(e) }
    fromPendingMain orElse fromPending orElse fromToBeExplored
  }

  private def exploreWith(url: URL) = { e: Explore => url == getUrl(e) }

  private def shouldIgnore(url: URL): Boolean = {
    def alreadyFetched = responses isDefinedAt url
    def alreadyPendingMainAuthority = pendingMainAuthority.isDefined && getUrl(pendingMainAuthority.get) == url
    def alreadyPending = pending isDefinedAt url
    def alreadyScheduled = toBeExplored exists exploreWith(url)
    alreadyFetched || alreadyPendingMainAuthority || alreadyPending || alreadyScheduled
  }

  def filteredExtractedURLs(urls: List[URL]): List[URL] =
    (urls filterNot shouldIgnore).distinct

  def withNewUrlsToBeExplored(urls: List[Explore]): Observation =
    this.copy(toBeExplored = toBeExplored ++ urls)

  val mainAuthority: Authority = strategy.mainAuthority

  lazy val pendingAuthorities: Set[Authority] = ((pendingMainAuthority map getUrl).toSet ++ pending.keySet) map { _.getAuthority }

  private def takeFromMainAuthority: Option[(Observation, Explore)] = {
    val optExplore = toBeExplored find { e => mainAuthority == getUrl(e).getAuthority }
//    println("=== "+toBeExplored)
//    println("&&& "+mainAuthority)
//    println("*** "+optExplore)
    optExplore map {
      case e @ (url, distance) =>
        (this.copy(
          toBeExplored = toBeExplored filterNot { url == getUrl(_) },
          pendingMainAuthority = optExplore),
          e)
    }
  }

  private def takeFromOtherAuthorities: Option[(Observation, Explore)] = {
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
   * TODO review documentation
   * returns the next best URL/Explore to fetch
   * best is defined in this order
   * <ul>
   * <li>a URL whose authority is the same as the main authority, if there is no pending fetch with this authority</li>
   * <li>a URL such that there is no pending fetch with the same authority</li>
   * <li>Nothing</li>
   * </ul>
   *
   * if no pending main authority, try to find one
   * if not, just take one whose authority is not already pending
   */
  def take: Option[(Observation, Explore)] = {
    //logger.debug(this.toString)
    if (pendingMainAuthority.isDefined) {
      takeFromOtherAuthorities
    } else {
      takeFromMainAuthority orElse takeFromOtherAuthorities
    }
  }

  val logger = play.Logger.of(classOf[Observation])

  def takeAtMost(n: Int): (Observation, List[Explore]) = {
    var current: Observation = this
    var explores: List[Explore] = List.empty
    for {
      i <- 0 to (n - 1)
      (observation, e) <- current.take
    } {
      current = observation
      explores ::= e
    }
    (current, explores)
  }

}

