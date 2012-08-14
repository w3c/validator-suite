package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.assertor._
import scalaz.Scalaz._
import scalaz._
import org.joda.time._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs.store.Binders._
import org.w3.vs.diesel._
import org.w3.vs.sparql._
import org.w3.banana.util._

object Run {

  def diff(l: Set[URL], r: Set[URL]): Set[URL] = {
    val d1 = l -- r
    val d2 = r -- l
    d1 ++ d2
  }

  def apply(id: RunId, job: Job, vo: RunVO)(implicit conf: VSConfiguration): Run = {
    import vo._
    Run(
      id = id,
      job = job,
      explorationMode = explorationMode,
      assertions = assertions,
      createdAt = createdAt,
      completedAt = completedAt,
      timestamp = timestamp,
      pending = Set.empty,
      resources = resources,
      errors = errors,
      warnings = warnings)
  }

  def bananaGet(orgId: OrganizationId, jobId: JobId, runId: RunId)(implicit conf: VSConfiguration): BananaFuture[Run] =
    bananaGet((orgId, jobId, runId).toUri)

  def get(orgId: OrganizationId, jobId: JobId, runId: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Run] =
    get((orgId, jobId, runId).toUri)

  def get(runUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Run] =
    bananaGet(runUri).toFutureVal

  def bananaGet(runUri: Rdf#URI)(implicit conf: VSConfiguration): BananaFuture[Run] = {
    import conf._
    val query = """
CONSTRUCT {
  ?run ont:job ?job .
  ?s1 ?p1 ?o1 .
  ?s2 ?p2 ?o2
} WHERE {
  BIND (iri(strbefore(str(?run), "#")) AS ?runG) .
  graph ?runG { ?run ont:job ?job } .
  BIND (iri(strbefore(str(?job), "#")) AS ?jobG) .
  {
    { 
      graph ?jobG { ?s1 ?p1 ?o1 }
    }
      UNION
    {
      graph ?runG { ?s2 ?p2 ?o2 }
    }
  }
}
"""
    val construct = ConstructQuery(query, ont)
    for {
      graph <- store.executeConstruct(construct, Map("run" -> runUri))
      pointedRun = PointedGraph[Rdf](runUri, graph)
      ids <- pointedRun.as[(OrganizationId, JobId, RunId)]
      runVO <- pointedRun.as[RunVO]
      jobVO <- (pointedRun / ont.job).as[JobVO]
    } yield {
      val job = Job(ids._2, jobVO)
      Run(ids._3, job, runVO)
    }
  }

  def getFor(orgId: OrganizationId, jobId: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Run]] =
    getFor((orgId, jobId).toUri)
    
  def getFor(jobUri: Rdf#URI)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Run]] = {
    import conf._
    val query = """
CONSTRUCT {
  ?job ont:run ?run .
  ?s1 ?p1 ?o1 .
  ?s2 ?p2 ?o2
} WHERE {
  BIND (iri(strbefore(str(?job), "#")) AS ?jobG) .
  {
    { 
      graph ?jobG { ?s1 ?p1 ?o1 }
    }
      UNION
    {
      graph ?jobG { ?job ont:run ?run } .
      BIND (iri(strbefore(str(?run), "#")) AS ?runG) .
      graph ?runG { ?s2 ?p2 ?o2 }
    }
  }
}
"""
    val construct = ConstructQuery(query, ont)
    val r = for {
      graph <- store.executeConstruct(construct, Map("job" -> jobUri))
      pointedJob = PointedGraph[Rdf](jobUri, graph)
      job <- pointedJob.as2[(OrganizationId, JobId), JobVO].map{ case ((_, id), vo) => Job(id, vo) }
      it <- (pointedJob / ont.run).asSet2[(OrganizationId, JobId, RunId), RunVO]
    } yield {
      it map { case ((_, _, runId), runVO) => Run(runId, job, runVO) }
    }
    r.toFutureVal
  }

  def save(run: Run)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf._
    val jobUri = run.toVO.context.toUri
    val r = for {
      _ <- store.put(run.ldr)
      _ <- store.append(jobUri, jobUri -- ont.run ->- run.runUri)
    } yield ()
    r.toFutureVal
  }

  def delete(run: Run)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")

  def getLatestRun(jobId: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Option[Run]] = {
    null
  }

}

/**
 * Run represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
case class Run(
    id: RunId,
    job: Job,
    explorationMode: ExplorationMode = ProActive,
    knownUrls: Set[URL] = Set.empty,
    toBeExplored: List[URL] = List.empty,
    fetched: Set[URL] = Set.empty,
    createdAt: DateTime,
    assertions: Set[Assertion] = Set.empty,
    completedAt: Option[DateTime],
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    pending: Set[URL] = Set.empty,
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    invalidated: Int = 0,
    pendingAssertions: Int = 0)(implicit conf: VSConfiguration) {

  val logger = play.Logger.of(classOf[Run])

  val runUri = (job.organization, job.id, id).toUri

  lazy val ldr: LinkedDataResource[Rdf] =
    LinkedDataResource(runUri, this.toVO.toPG)

  def jobData: JobData = JobData(resources, errors, warnings, createdAt, completedAt)
  
  def health: Int = jobData.health

  def strategy = job.strategy
  
  /**
   * Represent an article on the byUrl report
   * (resource url, last assertion timestamp, total warnings, total errors)
   * if there is no context, the assertion counts for 1
   */
  lazy val urlArticles: List[(URL, DateTime, Int, Int)] = {
    val uas = assertions.groupBy(_.url) map { case (url, as) =>
      val last = as.maxBy(_.timestamp).timestamp
      var errors = 0
      var warnings = 0
      as foreach { a =>
        a.severity match {
          case Error => errors += math.max(1, a.contexts.size)
          case Warning => warnings += math.max(1, a.contexts.size)
          case Info => ()
        }
      }
      (url, last, warnings, errors)
    }
    uas.toList
  }

  def urlArticle(url: URL): Option[(URL, DateTime, Int, Int)] =
    urlArticles find { _._1 === url }
  
  // Returns the assertors that validated @url, with their name and the total number of warnings and errors that they reported for @url.
  def assertorArticles(url: URL): List[(AssertorId, String, Int, Int)] = {
    val aas = assertions.filter(_.url === url).groupBy(_.assertorId) map { case (assertorId, as) =>
      var errors = 0
      var warnings = 0
      as foreach { a =>
        a.severity match {
          case Error => errors += math.max(1, a.contexts.size)
          case Warning => warnings += math.max(1, a.contexts.size)
          case Info => ()
        }
      }
      val assertorName = Assertor.getName(assertorId)
      (assertorId, assertorName, warnings, errors)
    }
    aas.toList
  }

  /* methods related to the data */
  
  def toVO: RunVO = RunVO((job.organization, job.id), explorationMode, createdAt, assertions, completedAt, timestamp, resources, errors, warnings)
  
  def numberOfKnownUrls: Int = knownUrls.count { _.authority === mainAuthority }

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */
  def noMoreUrlToExplore = pending.isEmpty && toBeExplored.isEmpty

  def isIdle = noMoreUrlToExplore && pendingAssertions == 0

  def isRunning = !isIdle

  def activity: RunActivity = if (isRunning) Running else Idle

  def state: (RunActivity, ExplorationMode) = (activity, explorationMode)

  private def shouldIgnore(url: URL): Boolean = {
    def notToBeFetched = IGNORE === strategy.getActionFor(url)
    def alreadyKnown = knownUrls contains url
    notToBeFetched || alreadyKnown
  }

  def numberOfRemainingAllowedFetches = strategy.maxResources - numberOfKnownUrls

  /**
   * Returns an Observation with the new urls to be explored
   */
  def withNewUrlsToBeExplored(urls: List[URL]): (Run, List[URL]) = {
    val filteredUrls = urls.filterNot{ url => shouldIgnore(url) }.distinct.take(numberOfRemainingAllowedFetches)
    val newData = this.copy(
      toBeExplored = toBeExplored ++ filteredUrls,
      knownUrls = knownUrls ++ filteredUrls)
    (newData, filteredUrls)
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
  def takeAtMost(n: Int): (Run, List[URL]) = {
    var current: Run = this
    var urls: List[URL] = List.empty
    for {
      i <- 1 to (n - pending.size)
      (observation, url) <- current.take
    } {
      current = observation
      urls ::= url
    }
    (current, urls.reverse)
  }

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

