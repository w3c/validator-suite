package org.w3.vs.model

import org.w3.vs._
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
import scalaz.Scalaz._
import org.joda.time.DateTime

object Run {

  def diff(l: Set[URL], r: Set[URL]): Set[URL] = {
    val d1 = l -- r
    val d2 = r -- l
    d1 ++ d2
  }

  def apply(job: Job)(implicit conf: VSConfiguration): Run = apply(job = job)
  
  def get(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Run] = sys.error("")

  def save(run: Run)(implicit conf: VSConfiguration): FutureVal[Exception, Run] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(run)
  }
  
}

/**
 * Run represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
case class Run(
    id: RunId = RunId(),
    explorationMode: ExplorationMode = ProActive,
    distance: Map[URL, Int] = Map.empty,
    toBeExplored: List[URL] = List.empty,
    fetched: Set[URL] = Set.empty,
    createdAt: DateTime = DateTime.now,
    job: Job,
    pending: Set[URL] = Set.empty,
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    invalidated: Int = 0,
    pendingAssertions: Int = 0)(implicit conf: VSConfiguration) {

  def data: JobData = JobData(job.id, resources, errors, warnings, createdAt)

  def strategy = job.strategy
  
  def save(): FutureVal[Exception, Run] = Run.save(this)
  
  def toValueObject: RunVO = RunVO(id, explorationMode, distance, toBeExplored, fetched, createdAt, job, resources, errors, warnings)
  
  type Explore = (URL, Int)

  def numberOfKnownUrls: Int = distance.keySet.count { _.authority === mainAuthority }

  // assert(
  //   distance.keySet == fetched ++ pending ++ toBeExplored,
  //   Run.diff(distance.keySet, fetched ++ pending ++ toBeExplored).toString)
  // assert(toBeExplored.toSet.intersect(pending) == Set.empty)
  // assert(pending.intersect(fetched) == Set.empty)
  // assert(toBeExplored.toSet.intersect(fetched) == Set.empty)

  val logger = play.Logger.of(classOf[Run])

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  def noMoreUrlToExplore = pending.isEmpty && toBeExplored.isEmpty

  def isIdle = noMoreUrlToExplore && pendingAssertions == 0

  def isRunning = !isIdle

  def activity: RunActivity = if (isRunning) Running else Idle

  def state: (RunActivity, ExplorationMode) = (activity, explorationMode)

  private def shouldIgnore(url: URL, atDistance: Int): Boolean = {
    def notToBeFetched = IGNORE == strategy.fetch(url, atDistance)
    def alreadyKnown = distance isDefinedAt url
    notToBeFetched || alreadyKnown
  }

  def numberOfRemainingAllowedFetches = strategy.maxResources - numberOfKnownUrls

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urls: List[URL], atDistance: Int): (Run, List[URL]) = {
    val filteredUrls = urls.filterNot{ url => shouldIgnore(url, atDistance) }.distinct.take(numberOfRemainingAllowedFetches)
    val newDistance = distance ++ filteredUrls.map { url => url -> atDistance }
    val newData = this.copy(
      toBeExplored = toBeExplored ++ filteredUrls,
      distance = newDistance)
    (newData, filteredUrls)
  }

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urlsWithDistance: List[(URL, Int)]): (Run, List[URL]) = {
    // as it's a map, there is no duplicated url :-)
    // also, the ListMap preserves the order of insertion

    val map: ListMap[URL, Int] = ListMap.empty
    map ++= urlsWithDistance.filterNot { case (url, distance) => shouldIgnore(url, distance) }
    val newUrls = map.keys.toList.take(numberOfRemainingAllowedFetches)
    val newData = this.copy(
      toBeExplored = toBeExplored ++ newUrls,
      distance = distance ++ map)
    (newData, newUrls)
  }

  val mainAuthority: Authority = strategy.mainAuthority

  /**
   * A consolidated view of all the authorities for the pending urls
   */
  lazy val pendingAuthorities: Set[Authority] = pending map { _.authority }

  /**
   * Returns a couple Observation/Explore.
   *
   * The Explore  is the first one that could be fetched for the main authority.
   *
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromMainAuthority: Option[(Run, URL)] = {
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
  private def takeFromOtherAuthorities: Option[(Run, URL)] = {
    val pendingToConsiderer =
      toBeExplored.view filterNot { url => url.authority == mainAuthority || (pendingAuthorities contains url.authority) }
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
  def take: Option[(Run, URL)] = {
    val take = if (mainAuthorityIsBeingFetched) {
      takeFromOtherAuthorities
    } else {
      takeFromMainAuthority orElse takeFromOtherAuthorities
    }
    take
  }

  /**
   * Returns as many Explores as possible to be fetched.
   *
   * The returned Observation has all the Explores marked as being pending.
   */
  def takeAtMost(n: Int): (Run, List[Explore]) = {
    var current: Run = this
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

//  def withCompletedFetch(url: URL): Run = this.copy(
//    pending = pending - url,
//    fetched = fetched + url,
//    data = data.copy(resources = data.resources + 1))
  
  def withResourceResponse(response: ResourceResponse): Run = this.copy(
    pending = pending - response.url,
    fetched = fetched + response.url,
    resources = response match {
      case _: HttpResponse => resources + 1
      case _ => resources
    }
  )

  def withAssertorResponse(response: AssertorResponse): Run = response match {
    case result: AssertorResult =>
      this.copy(
        errors = errors + result.errors,
        warnings = warnings + result.warnings,
        pendingAssertions = pendingAssertions - 1) // lower bound is 0
    case fail: AssertorFailure => this.copy(pendingAssertions = pendingAssertions - 1) // TODO? should do something about that
  }

  def stopMe(): Run =
    this.copy(explorationMode = Lazy, toBeExplored = List.empty)

  def withMode(mode: ExplorationMode) = this.copy(explorationMode = mode)
    
}

