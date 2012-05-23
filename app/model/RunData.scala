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

  def apply(job: Job): Run = apply(job = job)
  
  def apply(
      id: RunId = RunId(),
      job: Job,
      explorationMode: ExplorationMode = ProActive,
      distance: Map[URL, Int] = Map.empty,
      toBeExplored: List[URL] = List.empty,
      fetched: Set[URL] = Set.empty,
      createdAt: DateTime = DateTime.now,
      //jobData: JobData/* = JobData(jobId = job.id, runId = id)*/,
      pending: Set[URL] = Set.empty,
      invalidated: Int = 0,
      pendingAssertions: Int = 0): Run = {
    
    val jobData = JobData(jobId = job.id, runId = id)
    Run(RunVO(id, explorationMode, distance, toBeExplored, fetched, createdAt, job.id, jobData.id), job, pending, invalidated, pendingAssertions, jobData)
  }
  
  def get(id: RunId): FutureVal[Exception, Run] = sys.error("")
  def save(run: Run): FutureVal[Exception, Run] = sys.error("")
  
}

// Previously RunSnapshot
case class RunVO(
    id: RunId = RunId(),
    explorationMode: ExplorationMode = ProActive,
    distance: Map[URL, Int] = Map.empty,
    toBeExplored: List[URL] = List.empty,
    fetched: Set[URL] = Set.empty,
    createdAt: DateTime = DateTime.now,
    jobId: JobId,
    jobDataId: JobDataId = JobDataId())

/**
 * Run represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
// closed with job and jobData 
case class Run(
    valueObject: RunVO,
    job: Job,
    pending: Set[URL] = Set.empty,
    invalidated: Int = 0,
    pendingAssertions: Int = 0,
    data: JobData) {

  def id = valueObject.id
  def explorationMode = valueObject.explorationMode
  def distance = valueObject.distance
  def toBeExplored = valueObject.toBeExplored
  def fetched = valueObject.fetched
  def createdAt = valueObject.createdAt
  def strategy = job.strategy
  
  def save(): FutureVal[Exception, Run] = Run.save(this)
  
  type Explore = (URL, Int)

  final def numberOfKnownUrls: Int = distance.keySet.count { _.authority === mainAuthority }

  // assert(
  //   distance.keySet == fetched ++ pending ++ toBeExplored,
  //   Run.diff(distance.keySet, fetched ++ pending ++ toBeExplored).toString)
  // assert(toBeExplored.toSet.intersect(pending) == Set.empty)
  // assert(pending.intersect(fetched) == Set.empty)
  // assert(toBeExplored.toSet.intersect(fetched) == Set.empty)

  final val logger = play.Logger.of(classOf[Run])

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  final def noMoreUrlToExplore = pending.isEmpty && toBeExplored.isEmpty

  final def isIdle = noMoreUrlToExplore && pendingAssertions == 0

  final def isRunning = !isIdle

  final def activity: RunActivity = if (isRunning) Running else Idle

  final def state: (RunActivity, ExplorationMode) = (activity, explorationMode)

  private final def shouldIgnore(url: URL, atDistance: Int): Boolean = {
    def notToBeFetched = IGNORE == strategy.fetch(url, atDistance)
    def alreadyKnown = distance isDefinedAt url
    notToBeFetched || alreadyKnown
  }

  def numberOfRemainingAllowedFetches = strategy.maxNumberOfResources - numberOfKnownUrls

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urls: List[URL], atDistance: Int): (Run, List[URL]) = {
    val filteredUrls = urls.filterNot{ url => shouldIgnore(url, atDistance) }.distinct.take(numberOfRemainingAllowedFetches)
    val newDistance = distance ++ filteredUrls.map { url => url -> atDistance }
    val newData = this.copy(valueObject = valueObject.copy(
      toBeExplored = toBeExplored ++ filteredUrls,
      distance = newDistance))
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
    val newData = this.copy(valueObject = valueObject.copy(
      toBeExplored = toBeExplored ++ newUrls,
      distance = distance ++ map))
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
        valueObject = valueObject.copy(toBeExplored = toBeExplored filterNot { _ == url })),
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
        valueObject = valueObject.copy(toBeExplored = toBeExplored filterNot { _ == url })),
        url)
    }
  }

  lazy val mainAuthorityIsBeingFetched = pending exists { _.authority == mainAuthority }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched, giving priority to the main authority.
   */
  def take: Option[(Run, URL)] = {
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

  def withCompletedFetch(url: URL): Run = this.copy(
    pending = pending - url,
    valueObject = valueObject.copy(fetched = fetched + url))

  def withAssertorResponse(response: AssertorResponse): Run = response match {
    case result: AssertorResult => this.copy(data = data.withData(
      //oks = oks + (if (assertions.isValid) 1 else 0), // T: undefined
      errors = data.errors + result.errors,
      warnings = data.warnings + result.warnings),
      pendingAssertions = pendingAssertions - 1) // lower bound is 0
    case fail: AssertorFailure => this.copy(pendingAssertions = pendingAssertions - 1) // TODO? should do something about that
  }

  def stopMe(): Run =
    this.copy(valueObject = valueObject.copy(explorationMode = Lazy, toBeExplored = List.empty))

  def withMode(mode: ExplorationMode) = this.copy(valueObject = valueObject.copy(explorationMode = mode))
    
}

