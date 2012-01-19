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
import play.api.libs.iteratee.CallbackEnumerator


object Observation {
  
  def apply(_id: ObserverId, _strategy: Strategy): Observation =
    Observation(
      id = _id,
      strategy = _strategy,
      state = NotYetStarted,
      urlsToBeExplored = List.empty,
      responses = Map.empty,
      assertions = List.empty)
  
}

sealed trait ExplorationStatus
case object ToBeExplored extends ExplorationStatus
case object Pending extends ExplorationStatus

case class Explore(
    url: URL,
    distance: Int) {
  
  var status: ExplorationStatus = ToBeExplored
  
}

case class Observation(
    id: ObserverId,
    strategy: Strategy,
    state: ObserverState,
    urlsToBeExplored: List[Explore],
    responses: Map[URL, Response],
    assertions: Assertions) {
  
  val shortId = id.toString.substring(0,6)

  def urls: Iterable[URL] = responses.keys
  
  def explorationPhaseHasEnded = urlsToBeExplored.isEmpty
    
  def withFirstURLsToExplore(urls: Iterable[URL]): Observation =
    this.copy(urlsToBeExplored = urls.toList map { url => Explore(url, 0) })
  
  def stop(): Observation = this.copy(state = StoppedState)
  
  def withResponse(resp: (URL, Response)) =
    this.copy(responses = responses + resp)
  
//  def distanceFor(url: URL): Option[Int] =
//    pendingFetches collectFirst { case (_url, distance) if url == _url => distance }
  
  def distanceFor(url: URL): Option[Int] =
    urlsToBeExplored collectFirst { case e if url == e.url => e.distance }
  
  def withPendingFetch(url: URL): Observation = {
    urlsToBeExplored find { e => url == e.url } foreach { _.status = Pending }
    this
  }
  
  def withoutURLBeingExplored(url: URL): Observation =
    this.copy(urlsToBeExplored = urlsToBeExplored filterNot { e => url == e.url})
  
  private def shouldIgnore(url: URL): Boolean = {
    def alreadyFetched = responses isDefinedAt url
    def alreadyScheduled = urlsToBeExplored exists { e => url == e.url }
    alreadyFetched || alreadyScheduled
  }
  
  def withNewUrlsToBeExplored(urls: Iterable[Explore]): Observation = {
    val filteredURLs = urls filterNot { e => shouldIgnore(e.url) }
    this.copy(urlsToBeExplored = urlsToBeExplored ++ filteredURLs)
  }
  
  def numberOfKnownUrls: Int = responses.size + urlsToBeExplored.size
  
  /**
   * The main authority of the current strategy.
   * TODO: should be given by the strategy directly
   */
  val mainAuthority: Authority =
    strategy.seedURLs.headOption map {_.authority} getOrElse sys.error("No seed url in strategy")

  def fetch(atMost: Int)(f: (Explore, FetchAction) => Unit): Observation = {
    for {
      explore <- nextBestExplores(atMost)
    } {
      val Explore(url, distance) = explore
      val action = strategy.fetch(url, distance)
      // that's the hook with side-effects
      f(explore, action)
      explore.status = Pending
    }
    // but the status bits was changed for some explores
    this
  }
  
  /**
   * returns the next best URL/Explore to fetch
   * best is defined in this order
   * <ul>
   * <li>a URL whose authority is the same as the main authority, if there is no pending fetch with this authority</li>
   * <li>a URL such that there is no pending fetch with the same authority</li>
   * <li>Nothing</li>
   * </ul>
   * TODO: two pendingFetches ? One for main authority and one for the rest
   */
  def nextBestExplores(atMost: Int): Iterable[Explore] = {
    def alreadyPending(authority: Authority): Boolean =
      urlsToBeExplored exists { e => e.status == Pending && authority == e.url.getAuthority }
    val alreadyPendingFetchForMainAuthority = alreadyPending(mainAuthority)
    var counter: Int = 0
    // search for authority first
    // if there is already a pending request for the main authority, it is necessarily the first one
    val mainExplore = urlsToBeExplored find { mainAuthority == _.url.getAuthority }
    if (mainExplore.isDefined) {
      counter = 1
      mainExplore.get.status = Pending
    }
    val explores =
      for {
        explore <- urlsToBeExplored
        if counter < atMost && explore.status != Pending
      } yield {
        counter += 1
        explore.status = Pending
        explore
      }
    explores ++ mainExplore
  }
    
//  def withAssertions(assertions: Assertions): Observation =
//    this.copy(assertions = assertions)
    
}

