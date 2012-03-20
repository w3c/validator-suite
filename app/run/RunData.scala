package org.w3.vs.run

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import akka.dispatch._
import akka.actor._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit.MILLISECONDS
import play.Logger
import org.w3.vs.http._
import akka.util.duration._
import akka.util.Duration
import scala.collection.mutable.ListMap
import java.util.UUID

object RunData {

  def diff(l: Set[URL], r: Set[URL]): Set[URL] = {
    val d1 = l -- r
    val d2 = r -- l
    d1 ++ d2
  }

  def apply(strategy: Strategy, snapshot: RunSnapshot): RunData = {
    import snapshot._
    RunData(
      strategy = strategy,
      jobId = jobId,
      runId = runId,
      explorationMode = explorationMode,
      distance = distance,
      toBeExplored = toBeExplored,
      fetched = fetched,
      oks = oks,
      errors = errors,
      warnings = warnings)
  }

  def somethingImportantHappened(before: RunData, after: RunData): Boolean =
    before.explorationMode != after.explorationMode ||
      before.activity != after.activity

}

case class RunId(private val uuid: UUID)

object RunId {

  def fromString(s: String): RunId = RunId(UUID.fromString(s))

  def newId(): RunId = RunId(UUID.randomUUID())

}

case class Run(id: Run#Id) {
  type Id = UUID
}

/**
 * RunData represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
case class RunData(
    // will never change for a Run, but it's very usefull to have it here
    strategy: Strategy,
    jobId: JobId,
    runId: RunId = RunId.newId(),
    explorationMode: ExplorationMode = ProActive,
    // the distance from the seed for every known URLs
    distance: Map[URL, Int] = Map.empty,
    // state of each URL
    toBeExplored: List[URL] = List.empty,
    pending: Set[URL] = Set.empty,
    fetched: Set[URL] = Set.empty,
    // the set of actors subscribed to events
    subscribers: Set[ActorRef] = Set.empty,
    // keep track the assertions
    oks: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    invalidated: Int = 0,
    // keep track of assertions sent to the assertor we call synchronously
    sentAssertorResults: Int = 0,
    receivedAssertorResults: Int = 0) {

  type Explore = (URL, Int)

  final def numberOfKnownUrls: Int = distance.size

  // assert(
  //   distance.keySet == fetched ++ pending ++ toBeExplored,
  //   RunData.diff(distance.keySet, fetched ++ pending ++ toBeExplored).toString)
  // assert(toBeExplored.toSet.intersect(pending) == Set.empty)
  // assert(pending.intersect(fetched) == Set.empty)
  // assert(toBeExplored.toSet.intersect(fetched) == Set.empty)

  final val logger = play.Logger.of(classOf[RunData])

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  final def noMoreUrlToExplore = pending.isEmpty && toBeExplored.isEmpty

  final def isIdle = noMoreUrlToExplore && (sentAssertorResults == receivedAssertorResults)

  final def isBusy = !isIdle

  final def activity: RunActivity = if (isBusy) Busy else Idle

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
  def withNewUrlsToBeExplored(urls: List[URL], atDistance: Int): (RunData, List[URL]) = {
    val filteredUrls = urls.filterNot { url => shouldIgnore(url, atDistance) }.distinct
    val newDistance = distance ++ filteredUrls.map { url => url -> atDistance }
    val newData = this.copy(
      toBeExplored = toBeExplored ++ filteredUrls,
      distance = newDistance)
    (newData, filteredUrls)
  }

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urlsWithDistance: List[(URL, Int)]): (RunData, List[URL]) = {
    // as it's a map, there is no duplicated url :-)
    // also, the ListMap preserves the order of insertion

    val map: ListMap[URL, Int] = ListMap.empty
    map ++= urlsWithDistance.filterNot { case (url, distance) => shouldIgnore(url, distance) }
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
  private def takeFromMainAuthority: Option[(RunData, URL)] = {
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
  private def takeFromOtherAuthorities: Option[(RunData, URL)] = {
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
  def take: Option[(RunData, URL)] = {
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
  def takeAtMost(n: Int): (RunData, List[Explore]) = {
    var current: RunData = this
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

  def withCompletedFetch(url: URL): RunData = this.copy(
    pending = pending - url,
    fetched = fetched + url)

  def withAssertorResult(result: AssertorResult): RunData = result match {
    case assertions: Assertions => this.copy(
      oks = oks + assertions.numberOfOks,
      errors = errors + assertions.numberOfErrors,
      warnings = warnings + assertions.numberOfWarnings,
      receivedAssertorResults = receivedAssertorResults + 1)
    case fail: AssertorFail => this // should do something about that
  }

  def assertionPhaseIsFinished: Boolean =
    toBeExplored.isEmpty && sentAssertorResults == receivedAssertorResults

}

