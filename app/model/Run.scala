package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.vs.assertor._
import scalaz.{ Free, Equal }
import scalaz.Scalaz._
import org.joda.time._
import org.w3.vs.actor.AssertorCall
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._
import com.mongodb.{ QueryBuilder => _, _ }
import com.mongodb.util.JSON
import org.bson.types.ObjectId

object Run {

  val logger = play.Logger.of(classOf[Run])

  type Context = (UserId, JobId, RunId)

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("runs")

  def collection2(implicit conf: VSConfiguration): DBCollection =
    conf.mongoDb.getCollection("runs")

  def getAssertions(runId: RunId)(implicit conf: VSConfiguration): Future[List[Assertion]] = {
    import conf._
    val query = QueryBuilder().
      query( Json.obj(
        "runId" -> toJson(runId),
        "event" -> toJson("assertor-response"),
        "ar.assertions" -> Json.obj("$exists" -> JsBoolean(true))) ).
      projection( BSONDocument(
        "ar.assertions" -> BSONInteger(1),
        "_id" -> BSONInteger(0)) )
    val cursor = Run.collection.find[JsValue](query)
    cursor.toList map { list =>
      list.map(json => (json \ "ar" \ "assertions").as[List[Assertion]]).flatten
    }
  }

  def get(runId: RunId)(implicit conf: VSConfiguration): Future[(Run, Iterable[URL], Iterable[AssertorCall])] = Future {
    import scala.collection.JavaConverters._
    val query = new BasicDBObject("runId", new ObjectId(runId.toString))
    val orderedEvents =
      // needs to explicitly states the type to provide .asScala
      (collection2.find(query): java.lang.Iterable[DBObject]).asScala
        // bridge between Java driver and ReactiveMongo
        .map(dbObject => Json.parse(JSON.serialize(dbObject)))
        .map(_.as[RunEvent])
        .toList.sortBy(_.timestamp)
    val (createRun, events) = orderedEvents match {
      case (createRun@CreateRunEvent(_, _, _, _, _, _)) :: events => (createRun, events)
      case _ => sys.error("CreateRunEvent MUST be the first event")
    }
//    var i = 0
//    events foreach { event =>
//      i += 1
//      println(i)
//      println(event)
//    }
    Run.replayEvents(createRun, events)
  }  

//  def get(runId: RunId)(implicit conf: VSConfiguration): Future[(Run, Iterable[URL], Iterable[AssertorCall])] = {
//    val query = Json.obj("runId" -> toJson(runId))
//    val cursor = collection.find[JsValue, JsValue](query)
//    cursor.toList map { list =>
//      // the sort is done client-side
//      val orderedEvents = list.map(_.as[RunEvent]).sortBy(_.timestamp)
//      val (createRun, events) = orderedEvents match {
//        case (createRun@CreateRunEvent(_, _, _, _, _, _)) :: events => (createRun, events)
//        case _ => sys.error("CreateRunEvent MUST be the first event")
//      }
//      Run.replayEvents(createRun, events)
//    }
//  }  

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

  def saveEvent(event: RunEvent)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    collection.insert(toJson(event)) map { lastError => () }
  }

  /** replays all the events that define a run, starting with the CreateRunEvent
    * assumption: all events share the same runId and are ordered by their timestamp
    */
  def replayEvents(createRun: CreateRunEvent, events: Iterable[RunEvent]): (Run, Iterable[URL], Iterable[AssertorCall]) = {
    import createRun._
    val start = System.currentTimeMillis()
    var toBeFetched = Set.empty[URL]
    var toBeAsserted = Map.empty[(URL, AssertorId), AssertorCall]
    val (initialRun, urls) = Run((userId, jobId, runId), strategy, createdAt).newlyStartedRun
    var run = initialRun
    toBeFetched ++= urls
    events.toList.sortBy(_.timestamp) foreach {
      case CompleteRunEvent(userId, jobId, runId, at, _) => {
        run = run.completeOn(at)
      }
      case AssertorResponseEvent(runId, ar@AssertorResult(_, assertor, url, _), _) => {
        toBeAsserted -= ((url, assertor))
        run = run.withAssertorResult(ar)
      }
      case AssertorResponseEvent(runId, af@AssertorFailure(_, assertor, url, _), _) => {
        toBeAsserted -= ((url, assertor))
        run = run.withAssertorFailure(af)
      }
      case ResourceResponseEvent(runId, hr@HttpResponse(url, _, _, _, _, _), _) => {
        toBeFetched -= url
        val (newRun, urls, assertorCalls) = run.withHttpResponse(hr)
        run = newRun
        toBeFetched ++= urls
        assertorCalls foreach { ac =>
          toBeAsserted += ((ac.response.url, ac.assertor.id) -> ac)
        }
      }
      case ResourceResponseEvent(runId, er@ErrorResponse(url, _, _), _) => {
        toBeFetched -= url
        val (newRun, urls) = run.withErrorResponse(er)
        run = newRun
        toBeFetched ++= urls
      }
    }
    val result = (run, toBeFetched, toBeAsserted.values)
    val end = System.currentTimeMillis()
    logger.debug("Run deserialized in %dms (found %d events)" format (end - start, events.size))
    result
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

  import Run.logger

  val context: Run.Context = (userId, jobId, runId)

  val shortId: String = jobId.shortId + "/" + runId.shortId

  def jobData: JobData = JobData(numberOfFetchedResources, errors, warnings, createdAt, completedOn)
  
  def health: Int = jobData.health

  /* combinators */

  // set the completeAt bit and empty the queue of toBeExplored fetches
  def completeOn(at: DateTime): Run = this.copy(
    completedOn = Some(at),
    toBeExplored = List.empty)

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

//  def activity: RunActivity = if (isRunning) Running else Idle
//
//  def state: (RunActivity, ExplorationMode) = (activity, explorationMode)

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
    runWithResponse.takeAtMost(Strategy.maxUrlsToFetch)
  }

  def withHttpResponse(httpResponse: HttpResponse): (Run, Iterable[URL], Iterable[AssertorCall]) = {
    // add the new response
    val (runWithResponse, resourceInfo) = withResponse(httpResponse)
    resourceInfo match {
      case Fetched(_) => {
        // extract the urls to be explored
        val (runWithPendingFetches, urlsToFetch) =
          runWithResponse.withNewUrlsToBeExplored(httpResponse.extractedURLs).takeAtMost(Strategy.maxUrlsToFetch)
        // extract the calls to the assertor to be made
        val assertorCalls =
          if (httpResponse.method === GET) {
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
          runWithResponse.withNewUrlsToBeExplored(List(url)).takeAtMost(Strategy.maxUrlsToFetch)
        (runWithPendingFetches, urlsToFetch, List.empty)
      }
      case InfoError(why) => {
        val (runWithPendingFetches, urlsToFetch) =
          runWithResponse.takeAtMost(Strategy.maxUrlsToFetch)
        (runWithPendingFetches, urlsToFetch, List.empty)
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

}

