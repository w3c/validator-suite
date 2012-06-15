package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.store._
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
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._
import org.joda.time._
import org.w3.banana._

object Run {

  def diff(l: Set[URL], r: Set[URL]): Set[URL] = {
    val d1 = l -- r
    val d2 = r -- l
    d1 ++ d2
  }

  def getRunVO(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, RunVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = RunUri(id)
    FutureVal(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, RunVO]{
        val pointed = PointedGraph(uri, graph)
        RunVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }

  def get(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Run] = {
    for {
      vo <- getRunVO(id)
      job <- Job.get(vo.jobId)
    } yield {
      Run(
        id = vo.id,
        explorationMode = vo.explorationMode,
        createdAt = vo.createdAt,
        completedAt = vo.completedAt,
        timestamp = vo.timestamp,
        job = job,
        pending = Set.empty,
        resources = vo.resources,
        errors = vo.errors,
        warnings = vo.warnings,
        invalidated = 0,
        pendingAssertions = 0)
    }
  }

  def saveJobVO(vo: RunVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = RunVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(RunUri(vo.id), graph)
    FutureVal(result)
  }

  def save(run: Run)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveJobVO(run.toValueObject)
  
  def delete(run: Run)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")

  def getRunVOs(jobId: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[RunVO]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?runUri ?p ?o
} WHERE {
  graph ?g {
    ?runUri ont:jobId <#jobUri> .
    ?runUri ?p ?o
  }
}
""".replaceAll("#jobUri", JobUri(jobId).toString)
    val construct = SparqlOps.ConstructQuery(query, xsd, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraphVO(conf)(graph) }
  }

  def fromGraphVO(conf: VSConfiguration)(graph: conf.Rdf#Graph): Validation[BananaException, Iterable[RunVO]] = {
    import conf.diesel._
    import conf.binders._
    val vos: Iterable[Validation[BananaException, RunVO]] =
      graph.getAllInstancesOf(ont.Run) map { pointed => RunVOBinder.fromPointedGraph(pointed) }
    vos.toList.sequence[({type l[X] = Validation[BananaException, X]})#l, RunVO]
  }

}

/**
 * Run represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
case class Run(
    id: RunId = RunId(),
    explorationMode: ExplorationMode = ProActive,
    knownUrls: Set[URL] = Set.empty,
    toBeExplored: List[URL] = List.empty,
    fetched: Set[URL] = Set.empty,
    createdAt: DateTime,
    completedAt: Option[DateTime],
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    job: Job,
    pending: Set[URL] = Set.empty,
    resources: Int = 0,
    errors: Int = 0,
    warnings: Int = 0,
    invalidated: Int = 0,
    pendingAssertions: Int = 0)(implicit conf: VSConfiguration) {

  val logger = play.Logger.of(classOf[Run])

  // Q: what is jobData's timestamp value?
  // it should be completedAt but this one is an Option
  // jobData should itself be an Option[JobData]
  // and Job.getHistory should flatten the Option[JobData]s
  def jobData: JobData = JobData(id, resources, errors, warnings, createdAt, completedAt)
  
  def getAssertions: FutureVal[Exception, Iterable[Assertion]] = Assertion.getForRun(this)

  def getAssertions(url: URL): FutureVal[Exception, Iterable[Assertion]] = Assertion.getForRun(this, url)
  
  def health: Int = jobData.health

  def strategy = job.strategy
  
  def save(): FutureVal[Exception, Unit] = Run.save(this)
  
  def delete(): FutureVal[Exception, Unit] = Run.delete(this)

  // Represent an article on the byUrl report
  // (resource url, last assertion timestamp, total warnings, total errors)
  // TODO: optimize by writing the db request directly
  def getURLArticles(): FutureVal[Exception, Iterable[(URL, DateTime, Int, Int)]] = {
    for {
      assertions <- Assertion.getForRun(id)
    } yield {
      assertions.groupBy(_.url).map { case (url, it) => 
        (url, 
         it.map(_.timestamp).max,
         it.count(_.severity == Warning),
         it.count(_.severity == Error))
      }.filter(t => t._3 != 0 || t._4 != 0).toSeq.sortBy(_._1.toString).sortBy(e => -e._4)
    }
  }

  def getURLArticle(url: URL): FutureVal[Exception, (URL, DateTime, Int, Int)] = {
    getURLArticles().map{it => it.find(_._1 == url)} discard {
      case None => new Exception("Unknown URL") //TODO type exception
    } map {
      case a => a.get
    }
  }
  
  // TODO
  // Returns the assertors that validated @url, with their name and the total number of warnings and errors that they reported for @url.
  def getAssertorArticles(url: URL): FutureVal[Exception, Iterable[(AssertorId, String, Int, Int)]] = {
    implicit val context = org.w3.vs.Prod.configuration.webExecutionContext
    FutureVal.successful(Iterable(
      (ValidatorNu.id, Assertor.getName(ValidatorNu.id), 9999, 9999),
      (CSSValidator.id, Assertor.getName(CSSValidator.id), 9999, 9999)
    ))
  }

  /* methods related to the data */
  
  def toValueObject: RunVO = RunVO(id, job.id, explorationMode, createdAt, completedAt, timestamp, resources, errors, warnings)
  
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

