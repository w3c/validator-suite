package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.assertor._
import scalaz.{ Free, Equal }
import scalaz.Scalaz._
import org.joda.time._
import org.w3.banana._
import org.w3.banana.LinkedDataStore._
import org.w3.vs.store.Binders._
import org.w3.vs.diesel._
import org.w3.vs.diesel.ops._
import org.w3.vs.sparql._
import org.w3.vs.actor.AssertorCall
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global

object Run {
  
  type Context = (UserId, JobId, RunId)

  def get(userId: UserId, jobId: JobId, runId: RunId)(implicit conf: VSConfiguration): Future[(Run, Iterable[URL], Iterable[AssertorCall])] =
    get((userId, jobId, runId).toUri)

  def get(runUri: Rdf#URI)(implicit conf: VSConfiguration): Future[(Run, Iterable[URL], Iterable[AssertorCall])] = {
    import conf._
    store.asLDStore.GET(runUri) flatMap { ldr =>
      // there is a bug in banana preventing the implicit to be discovered
      RunFromPG.fromPointedGraph(ldr.resource).asFuture
    }
  }

  def saveAsScript(run: Run): Free[({type l[+x] = Command[Rdf, x]})#l, Unit] = {
    val jobUri = (run.userId, run.jobId).toUri
    val script = for {
      _ <- Command.PUT[Rdf](run.ldr)
      _ <- Command.PATCH[Rdf](jobUri, tripleMatches = List((jobUri, ont.run.uri, ANY)))
      _ <- Command.POST[Rdf](jobUri, jobUri -- ont.run ->- run.runUri)
    } yield ()
    script
  }

  @deprecated("", "")
  def save(run: Run)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val script = saveAsScript(run)
    store.execute(script)
  }

  def delete(run: Run)(implicit conf: VSConfiguration): Future[Unit] =
    sys.error("")

  def apply(context: Run.Context, strategy: Strategy): Run =
    new Run(context._1, context._2, context._3, strategy)

  def apply(context:Run.Context, strategy: Strategy, createdAt: DateTime): Run =
    new Run(context._1, context._2, context._3, strategy, createdAt)

  def freshRun(userId: UserId, jobId: JobId, strategy: Strategy): Run = {
    new Run(userId, jobId, RunId(), strategy = strategy)
  }

  /* addResourceResponse */

  def saveEventAsScript(runUri: Rdf#URI, event: RunEvent): Free[({type l[+x] = Command[Rdf, x]})#l, Unit] = {
    Command.POST[Rdf](runUri, runUri -- ont.event ->- event.toPG)
  }

  @deprecated("", "")
  def saveEvent(runUri: Rdf#URI, event: RunEvent)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val script = saveEventAsScript(runUri, event)
    store.execute(script)
  }

  /* other events */

  def completeAsScript(jobUri: Rdf#URI, runUri: Rdf#URI, at: DateTime): Free[({type l[+x] = Command[Rdf, x]})#l, Unit] = {
    val script = for {
      _ <- Command.PATCH[Rdf](jobUri, tripleMatches = List((jobUri, ont.lastRun.uri, ANY)))
      _ <- Command.POST[Rdf](jobUri, jobUri -- ont.lastRun ->- runUri)
      _ <- Command.POST[Rdf](runUri, runUri -- ont.completedOn ->- at)
    } yield ()
    script
  }

  @deprecated("", "")
  def complete(jobUri: Rdf#URI, runUri: Rdf#URI, at: DateTime)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val script = completeAsScript(jobUri, runUri, at)
    store.execute(script)
  }

}

/**
 * Run represents a coherent state of for an Run, modelized as an FSM
 * see http://akka.io/docs/akka/snapshot/scala/fsm.html
 */
case class Run private (
  userId: UserId,
  jobId: JobId,
  runId: RunId,
  strategy: Strategy,
  createdAt: DateTime = DateTime.now(DateTimeZone.UTC),
  // from completion event, None at creation
  completedOn: Option[DateTime] = None,
  // from user event, ProActive by default at creation
  explorationMode: ExplorationMode = ProActive,
  // based on scheduled fetches
  toBeExplored: List[URL] = List.empty,
  pending: Set[URL] = Set.empty,
  // based on added resources
  knownResources: Map[URL, ResourceInfo] = Map.empty,
  // based on added assertions
  assertions: Set[Assertion] = Set.empty,
  errors: Int = 0,
  warnings: Int = 0,
  invalidated: Int = 0,
  // based on scheduled assertions
  pendingAssertions: Set[(AssertorId, URL)] = Set.empty) {

  val context: Run.Context = (userId, jobId, runId)

  val logger = play.Logger.of(classOf[Run])

  val shortId: String = jobId.shortId + "/" + runId.shortId

  val runUri = (userId, jobId, runId).toUri

  def jobData: JobData = JobData(numberOfFetchedResources, errors, warnings, createdAt, completedOn)
  
  def health: Int = jobData.health

  /* combinators */

  // set the completeAt bit and empty the queue of toBeExplored fetches
  def completeOn(at: DateTime): Run = this.copy(
    completedOn = Some(at),
    toBeExplored = List.empty)

  /* methods related to the data */
  
  def ldr: LinkedDataResource[Rdf] = LinkedDataResource(runUri, this.toPG)

  // should we also count the errors and redirections?
  lazy val numberOfFetchedResources: Int = {
    knownResources.values count {
      case Fetched(_) => true
      case _ => false
    }
  }

  def knownUrls: Iterable[URL] = pending.toIterable ++ toBeExplored ++ knownResources.keys

  // same question here: what about Errors and Redirects?
  def numberOfKnownUrls: Int = numberOfFetchedResources + toBeExplored.size + pending.size

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */

  def noMoreUrlToExplore: Boolean = (numberOfRemainingAllowedFetches == 0) || (pending.isEmpty && toBeExplored.isEmpty)

  def hasNoPendingAction: Boolean = noMoreUrlToExplore && pendingAssertions.isEmpty

  def isIdle: Boolean = (noMoreUrlToExplore && hasNoPendingAction) || completedOn.isDefined

  def isRunning: Boolean = !isIdle

  def activity: RunActivity = if (isRunning) Running else Idle

  def state: (RunActivity, ExplorationMode) = (activity, explorationMode)

  private def shouldIgnore(url: URL): Boolean = {
    def notToBeFetched = IGNORE === strategy.getActionFor(url)
    def alreadyKnown = knownResources.isDefinedAt(url)
    notToBeFetched || alreadyKnown
  }

  lazy val numberOfRemainingAllowedFetches = strategy.maxResources - numberOfFetchedResources

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
      toBeExplored filterNot { url => url.authority == mainAuthority || (pendingAuthorities contains url.authority) }
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
  private def takeAtMost(n: Int): (Run, Iterable[URL]) = {
    var current: Run = this
    var urls: List[URL] = List.empty
    for {
      i <- 1 to math.min(numberOfRemainingAllowedFetches, n - pending.size)
      (run, url) <- current.take
    } {
      current = run
      urls ::= url
    }
    (current, urls)
  }

  /**
   * Returns an Observation with the new urls to be explored
   */
  private def withNewUrlsToBeExplored(urls: List[URL]): Run = {
    val filteredUrls = urls.filterNot{ url => shouldIgnore(url) }.distinct
    // if (! filteredUrls.isEmpty)
    //   logger.debug("%s: Found %d new urls to explore. Total: %d" format (shortId, filteredUrls.size, this.numberOfKnownUrls))
    val run = this.copy(toBeExplored = toBeExplored ++ filteredUrls)
    run
  }

  private def withResponse(response: ResourceResponse): (Run, ResourceInfo) = {
    val resourceInfo = ResourceInfo(response)
    val run = this.copy(
      pending = pending - response.url,
      knownResources = this.knownResources + (response.url -> resourceInfo)
    )
    (run, resourceInfo)
  }

  def newlyStartedRun: (Run, Iterable[URL]) =
    this.withNewUrlsToBeExplored(List(strategy.entrypoint)).takeAtMost(Strategy.maxUrlsToFetch)

  def withErrorResponse(errorResponse: ErrorResponse): (Run, Iterable[URL]) = {
    val runWithResponse = withResponse(errorResponse)._1
    if (explorationMode === ProActive)
      runWithResponse.takeAtMost(Strategy.maxUrlsToFetch)
    else
      (runWithResponse, Iterable.empty[URL])
  }

  def withHttpResponse(httpResponse: HttpResponse): (Run, Iterable[URL], Iterable[AssertorCall]) = {
    // add the new response
    val (runWithResponse, resourceInfo) = withResponse(httpResponse)
    resourceInfo match {
      case Fetched(_) => {
        // extract the urls to be explored
        val (runWithPendingFetches, urlsToFetch) =
          if (explorationMode === ProActive)
            runWithResponse.withNewUrlsToBeExplored(httpResponse.extractedURLs).takeAtMost(Strategy.maxUrlsToFetch)
          else
            (runWithResponse, Set.empty[URL])
       // extract the calls to the assertor to be made
       val assertorCalls =
         if (explorationMode === ProActive && httpResponse.method === GET) {
           val assertors = strategy.getAssertors(httpResponse)
           assertors map { assertor => AssertorCall(this.context, assertor, httpResponse) }
         } else {
           Set.empty[AssertorCall]
         }
        val runWithPendingAssertorCalls =
          runWithPendingFetches.copy(pendingAssertions = runWithPendingFetches.pendingAssertions ++ assertorCalls.map(ac => (ac.assertor.id, ac.response.url)))
        (runWithPendingAssertorCalls, urlsToFetch, assertorCalls)
      }
      case Redirect(_, url) => {
        // extract the urls to be explored
        val (runWithPendingFetches, urlsToFetch) =
          if (explorationMode === ProActive)
            runWithResponse.withNewUrlsToBeExplored(List(url)).takeAtMost(Strategy.maxUrlsToFetch)
          else
            (runWithResponse, Set.empty[URL])
        (runWithPendingFetches, urlsToFetch, List.empty)
      }
      case InfoError(_) => {
        (runWithResponse, List.empty, List.empty)
      }
    }

  }

  def withAssertorResult(result: AssertorResult): Run = {
    // Tom: Ignore assertions for documents that were already validated by the same assertor. This supposes that
    // there cannot be any partial validations, which is true for all W3C assertors. XXX
    val filteredAssertions = result.assertions.groupBy(_.url).filter{case (url, _) =>
      ! assertions.exists{case assertion =>
        assertion.assertor == result.assertor && assertion.url == url
      }
    }.map(_._2).flatten

    val (nbErrors, nbWarnings) = Assertion.countErrorsAndWarnings(filteredAssertions)
    this.copy(
      assertions = this.assertions ++ filteredAssertions,
      errors = this.errors + nbErrors,
      warnings = this.warnings + nbWarnings,
      pendingAssertions = pendingAssertions - ((result.assertor, result.sourceUrl)))
  }

  def withAssertorFailure(fail: AssertorFailure): Run = {
    this.copy(pendingAssertions = pendingAssertions - ((fail.assertor, fail.sourceUrl)))
  }

  def stopMe(): Run =
    this.copy(explorationMode = Lazy, toBeExplored = List.empty)

  def withMode(mode: ExplorationMode) = this.copy(explorationMode = mode)
    
}

