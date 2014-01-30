package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.util.implicits._
import org.w3.vs.util.iteratee._
import org.w3.vs.store.MongoStore.journalCommit
import org.w3.vs.web._
import org.w3.vs.assertor._
import scalaz.{ Free, Equal }
import scalaz.Scalaz._
import org.joda.time._
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.iteratee._

// Reactive Mongo imports
import reactivemongo.bson._
import reactivemongo.api.collections.default.BSONCollection
import play.modules.reactivemongo.json.ImplicitBSONHandlers._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

object Run {

  val logger = play.Logger.of(classOf[Run])

  def collection(implicit conf: Database): BSONCollection =
    conf.db("runs")

  def getAssertions(runId: RunId)(implicit conf: Database): Future[Seq[Assertion]] = {
    import conf._
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> toJson("assertor-response"),
      "ar.assertions" -> Json.obj("$exists" -> JsBoolean(true)))
    val projection = BSONDocument(
      "ar.assertions" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = Run.collection.find(query, projection).cursor[JsValue]
    cursor.enumerate() &> Enumeratee.map[JsValue] { json =>
      val assertions: Seq[Seq[Assertion]] =
        (json \ "ar" \ "assertions").as[JsArray].value.map(_.\("assertions").as[Seq[Assertion]])
      assertions.flatten
    } |>>> Iteratee.consume[Seq[Assertion]]()
  }

  def getAssertionsForURL(runId: RunId, url: URL)(implicit conf: Database): Future[Seq[Assertion]] = {
    import conf._
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> toJson("assertor-response"),
      "ar.assertions" -> Json.obj("$elemMatch" -> Json.obj("url" -> toJson(url))))
    val projection = Json.obj(
      // sadly, the following projection is not supported:
      // "ar.assertions" -> Json.obj("$elemMatch" -> Json.obj("url" -> toJson(url))),
      // this ends with: MongoError['Cannot use $elemMatch projection on a nested field (currently unsupported).' (code = 16344)]
      // see https://jira.mongodb.org/browse/SERVER-831
      "ar.assertions" -> toJson(1),
      "_id" -> toJson(0))
    val cursor = Run.collection.find(query, projection).cursor[JsValue]
    cursor.enumerate() &> Enumeratee.map[JsValue] { json =>
      val assertions: Seq[Seq[Assertion]] =
        (json \ "ar" \ "assertions").as[JsArray].value.collect { case json if (json \ "url") == toJson(url) =>
          (json \ "assertions").as[Seq[Assertion]]
        }
      assertions.flatten
    } |>>> Iteratee.consume[Seq[Assertion]]()
  }

  /** gets the final ResourceData for the given `run` and `url`
    */
  def getResourceDataForURL(runId: RunId, url: URL)(implicit conf: Database): Future[ResourceData] = {
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> "done-run")
    val projection = BSONDocument(
      "rd" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = collection.find(query, projection).cursor[JsValue]
    cursor.headOption flatMap {
      case None => Future.failed(new NoSuchElementException(s"${runId} does not exist or is not in Done state"))
      case Some(json) =>
        (json \ "rd").as[JsArray].value.collectFirst { case json if (json \ "url") == toJson(url) =>
          (json \ "rd").as[ResourceData]
        } match {
          case None => Future.failed(new NoSuchElementException(url.toString))
          case Some(rd) => Future.successful(rd)
        }
    }
  }

  /** gets all final ResourceData-s for the given `run` and `url`
    */
  def getResourceDatas(runId: RunId)(implicit conf: Database): Future[Seq[ResourceData]] = {
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> "done-run")
    val projection = BSONDocument(
      "rd" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = collection.find(query, projection).cursor[JsValue]
    cursor.headOption flatMap {
      case None => Future.failed(new NoSuchElementException(s"${runId} does not exist or is not in Done state"))
      case Some(json) => Future.successful {
        val rds: Seq[ResourceData] =
          (json \ "rd").as[JsArray].value.map(json => (json \ "rd").as[ResourceData])
        rds
      }
    }
  }

  /** gets all final ResourceData-s for the given `run` and `url`
    */
  def getGroupedAssertionDatas(runId: RunId)(implicit conf: Database): Future[Seq[GroupedAssertionData]] = {
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> "done-run")
    val projection = BSONDocument(
      "gad" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = collection.find(query, projection).cursor[JsValue]
    cursor.headOption flatMap {
      case None => Future.failed(new NoSuchElementException(s"${runId} does not exist or is not in Done state"))
      case Some(json) => Future.successful {
        (json \ "gad").as[Seq[GroupedAssertionData]]
      }
    }
  }

  /** returns the data that defines the final state of a Run.
    */
  @deprecated("if the Job is Done, then RunData is already available there", "")
  def getFinalRunData(runId: RunId)(implicit conf: Database): Future[RunData] = {
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> "done-run")
    val projection = BSONDocument(
      "runData" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = collection.find(query, projection).cursor[JsValue]
    cursor.headOption flatMap {
      case None => Future.failed(new NoSuchElementException(runId.toString))
      case Some(json) => Future.successful((json \ "runData").as[RunData])
    }
  }

  /** returns the data that defines the final state of a Run.
    */
  @deprecated("already in Job's Done state", "")
  def getPartialJobData(runId: RunId)(implicit conf: Database): Future[(DateTime, RunData)] = {
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> "done-run")
    val projection = BSONDocument(
      "timestamp" -> BSONInteger(1),
      "runData" -> BSONInteger(1),
      "_id" -> BSONInteger(0))
    val cursor = collection.find(query, projection).cursor[JsValue]
    cursor.headOption flatMap {
      case None => Future.failed(new NoSuchElementException(runId.toString))
      case Some(json) => Future.successful {
        val timestamp = (json \ "timestamp").as[DateTime]
        val runData = (json \ "runData").as[RunData]
        (timestamp, runData)
      }
    }
  }

  def enumerateRunEvents(runId: RunId)(implicit conf: Database): Enumerator[Iterator[RunEvent]] = {
    val query = Json.obj("runId" -> toJson(runId))
    val cursor = collection.find(query).cursor[JsValue]
//    cursor.enumerateBulks() &> Enumeratee.map[JsValue](_.as[RunEvent])
    cursor.enumerateBulks() &> Enumerateerator.map[JsValue](_.as[RunEvent])
  }

  def get(runId: RunId)(implicit conf: Database): Future[(Run, Iterable[RunAction])] = {
    val query = Json.obj("runId" -> toJson(runId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.collect[List]() map { list =>
      // the sort is done client-side
      val orderedEvents = list.map(_.as[RunEvent]).sortBy(_.timestamp)
      val (createRun, events) = orderedEvents match {
        case (createRun@CreateRunEvent(_, _, _, _, _, _)) :: events => (createRun, events)
        case _ => sys.error("CreateRunEvent MUST be the first event")
      }
      Run.replayEvents(createRun, events)
    }
  }  

  def delete(run: Run)(implicit conf: Database): Future[Unit] =
    sys.error("")

  /** removes all the [[org.w3.vs.model.RunEvent]]s with the given runId */
  def removeAll(runId: RunId)(implicit conf: Database): Future[Unit] = {
    val query = Json.obj("runId" -> toJson(runId))
    collection.remove[JsValue](query) map { lastError => () }
  }

  def apply(runId: RunId, strategy: Strategy): Run =
    new Run(runId, strategy)

  def freshRun(strategy: Strategy): Run = {
    new Run(RunId(), strategy = strategy)
  }

  /* addResourceResponse */

  def saveEvent(event: RunEvent)(implicit conf: Database): Future[Unit] = {
    import conf._
    // default writeConcern here as we don't care about waiting for
    // the actual Write
    collection.insert(toJson(event)) map { lastError =>
      if (!lastError.ok) throw lastError
    }
  }

  /** replays all the events that define a run, starting with the CreateRunEvent
    * assumption: all events share the same runId and are ordered by their timestamp
    */
  def replayEvents(createRun: CreateRunEvent, events: Iterable[RunEvent]): (Run, Iterable[RunAction]) = {
    import createRun._
    val start = System.currentTimeMillis()
    var run = Run(runId, strategy).step(createRun).run
    events foreach { event =>
      run = run.step(event).run
    }
    val toBeFetched = run.pendingFetches.values
    val toBeAsserted = run.pendingAssertorCalls.values
    val result = (run, toBeFetched ++ toBeAsserted)
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
  runId: RunId,
  strategy: Strategy,
  // part of the state that we maintain
  resourceDatas: Map[URL, ResourceData] = Map.empty,
  groupedAssertionDatas: Map[AssertionTypeId, GroupedAssertionData] = Map.empty,
  // from completion event, None at creation
  completedOn: Option[DateTime] = None,
  // based on scheduled fetches
  toBeExplored: List[URL] = List.empty,
  pendingFetches: Map[URL, Fetch] = Map.empty,
  // based on added resources
  knownResources: Map[URL, ResourceInfo] = Map.empty,
  // based on added assertions
  assertions: Map[URL, Map[AssertorId, Vector[Assertion]]] = Map.empty,
  errors: Int = 0,
  warnings: Int = 0,
  // based on scheduled assertions
  pendingAssertorCalls: Map[(AssertorId, URL), AssertorCall] = Map.empty,
  assertorResponsesReceived: Int = 0) {

  import Run.logger

  def shortId: String = runId.shortId.toString

  /**
   * Note: This indicator is implemented for user feedback and its exact semantic is up to
   * the implementation. Hence no logic should rely on this value.
   * @return an indication of the progress of the Run, between 0 and 100
   */
  def progress: Int = {
    val expectedFetchs: Double = strategy.maxResources.toDouble
    val crawlProgress =
      if (pendingFetches.size > 0 && expectedFetchs > 0) {
        // How many have we fetched compared to the max we can go
        (numberOfFetchedResources / expectedFetchs * 100)
      } else {
        // No pending fetches. We have either reached the limit of knownUrls or the max number of pages.
        100 // or no pending fetch yet!
      }

    val expectedAssertorCalls: Double =
      if (crawlProgress == 100)
        numberOfFetchedResources * (AssertorsConfiguration.default.size) // only assertors that accept html documents, i.e. all for now
      else
        expectedFetchs * (AssertorsConfiguration.default.size)

    val assertionProgress =
     if (expectedAssertorCalls == 0) 100
     else (assertorResponsesReceived / expectedAssertorCalls * 100)

    scala.math.min(crawlProgress, assertionProgress).toInt
  }

  def data: RunData = RunData(numberOfFetchedResources, errors, warnings, jobDataStatus, completedOn)
  
  def health: Int = data.health

  def jobDataStatus: JobDataStatus =
    if (completedOn.isDefined) JobDataIdle else JobDataRunning(progress)

  /* combinators */

  // set the completeAt bit and empty the queue of toBeExplored fetches
  def completeOn(at: DateTime): Run = this.copy(
    completedOn = Some(at),
    toBeExplored = List.empty)

  lazy val numberOfFetchedResources: Int = {
    knownResources.values count {
      case Fetched(status) if (status < 300) && (status >= 200) => true
      case _ => false
    }
  }

  def knownUrls: Iterable[URL] = pendingFetches.keys ++ toBeExplored ++ knownResources.keys

  // same question here: what about Errors and Redirects?
  def numberOfKnownUrls: Int = numberOfFetchedResources + toBeExplored.size + pendingFetches.size

  /**
   * An exploration is over when there are no more urls to explore and no pending url
   */

  def noMoreUrlToExplore: Boolean = (numberOfRemainingAllowedFetches === 0) || (pendingFetches.isEmpty && toBeExplored.isEmpty)

  def hasNoPendingAction: Boolean = noMoreUrlToExplore && pendingAssertorCalls.isEmpty

  def isIdle: Boolean = (noMoreUrlToExplore && hasNoPendingAction) || completedOn.isDefined

  def isRunning: Boolean = !isIdle

  def allAssertions: Iterable[Assertion] = {
    assertions.values.flatMap(_.values.flatten)
  }

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
  lazy val pendingAuthorities: Set[Authority] = pendingFetches.keySet map { _.authority }

  private def actionFor(url: URL): Option[Fetch] = {
    val action = strategy.getActionFor(url)
    action match {
      case GET => Some(Fetch(url, GET))
      case HEAD => Some(Fetch(url, HEAD))
      case IGNORE => None
    }
  }

  /**
   * Returns a couple Observation/Explore.
   *
   * The Explore  is the first one that could be fetched for the main authority.
   *
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromMainAuthority: Option[(Run, Fetch)] = {
    for {
      url <- toBeExplored.find { _.authority === mainAuthority }
      fetch <- actionFor(url)
    } yield {
      val run = this.copy(
        pendingFetches = pendingFetches + (url -> fetch),
        toBeExplored = toBeExplored.filterNot { _ === url }
      )
      (run, fetch)
    }
  }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched for any authority but the main one.
   * Also, this Explore must be the only one with this Authority.
   *
   * The returned Observation has set this Explore to be pending.
   */
  private def takeFromOtherAuthorities: Option[(Run, Fetch)] = {
    for {
      url <- toBeExplored.filterNot { url => url.authority === mainAuthority || pendingAuthorities.contains(url.authority) }.headOption
      fetch <- actionFor(url)
    } yield {
      val run = this.copy(
        pendingFetches = pendingFetches + (url -> fetch),
        toBeExplored = toBeExplored.filterNot { _ === url }
      )
      (run, fetch)
    }
  }

  lazy val mainAuthorityIsBeingFetched = pendingFetches.keys.exists { _.authority === mainAuthority }

  /**
   * Returns (if possible, hence the Option) the first Explore that could
   * be fetched, giving priority to the main authority.
   */
  private def take: Option[(Run, Fetch)] = {
    val take =
      if (mainAuthorityIsBeingFetched) {
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
  private def takeAtMost(n: Int): (Run, List[Fetch]) = {
    var current: Run = this
    var fetches: List[Fetch] = List.empty
    for {
      i <- 1 to math.min(numberOfRemainingAllowedFetches, n - pendingFetches.size)
      (run, fetch) <- current.take
    } {
      current = run
      fetches ::= fetch
    }
    (current, fetches)
  }

  /** apply one event to the current Run. This targets both the
    * JobActor -- which generates the RunEvents -- and any external
    * process that wants to retrieve a consistent state.
    * 
    * @return the new Run with some potential actions
    */
  def step(event: RunEvent): ResultStep = {
    val resultStep = event match {
      case CreateRunEvent(userId, jobId, runId, actorPath, strategy, timestamp) =>
        val (run, fetches) = this.newlyStartedRun
        ResultStep(run, fetches)

      case DoneRunEvent(userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) =>
        val run = this.completeOn(timestamp)
        ResultStep(run, Seq.empty)

      case are@AssertorResponseEvent(userId, jobId, runId, ar@AssertorResult(assertor, url, _), timestamp) =>
        val run = this.withAssertorResult(ar, timestamp)
        ResultStep(run, Seq.empty)

      case AssertorResponseEvent(userId, jobId, runId, af@AssertorFailure(assertor, url, _), _) =>
        val run = this.withAssertorFailure(af)
        ResultStep(run, Seq.empty)

      case ResourceResponseEvent(userId, jobId, runId, hr@HttpResponse(url, _, _, _, _, _), _) =>
        val (run, fetches, assertorCalls) = this.withHttpResponse(hr)
        val actions = fetches ++ assertorCalls
        ResultStep(run, actions)

      case ResourceResponseEvent(userId, jobId, runId, er@ErrorResponse(url, _, _), _) =>
        val (run, fetches) = this.withErrorResponse(er)
        ResultStep(run, fetches)
    }

    def notCancel = event match {
      case DoneRunEvent(_, _, _, Cancelled, _, _, _, _, _, _) => false
      case _ => true
    }

    // a Run must go into the Completed state if and only if
    // * it has no pending action
    // * it was not cancelled
    // * it's not already Completed
    if (resultStep.run.hasNoPendingAction && notCancel && resultStep.run.completedOn.isEmpty) {
      val timestamp = DateTime.now(DateTimeZone.UTC)
      val completedRun = resultStep.run.completeOn(timestamp)
      val data = completedRun.data
      val completeRunEvent = DoneRunEvent(event.userId, event.jobId, runId, Completed, data.resources, data.errors, data.warnings, completedRun.resourceDatas, completedRun.groupedAssertionDatas.values, timestamp)
      resultStep.copy(
        run = completedRun,
        actions = resultStep.actions :+ EmitEvent(completeRunEvent)
      )
    } else {
      resultStep
    }

  }

  /**
   * Returns an Run with the new urls to be explored
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
      pendingFetches = pendingFetches - response.url,
      knownResources = this.knownResources + (response.url -> resourceInfo)
    )
    (run, resourceInfo)
  }

  private def newlyStartedRun: (Run, List[Fetch]) =
    this.withNewUrlsToBeExplored(List(strategy.entrypoint)).takeAtMost(Strategy.maxUrlsToFetch)

  private def withErrorResponse(errorResponse: ErrorResponse): (Run, List[Fetch]) = {
    val runWithResponse = withResponse(errorResponse)._1
    runWithResponse.takeAtMost(Strategy.maxUrlsToFetch)
  }

  private def withHttpResponse(httpResponse: HttpResponse): (Run, List[Fetch], Iterable[AssertorCall]) = {
    // add the new response
    val (runWithResponse, resourceInfo) = withResponse(httpResponse)
    resourceInfo match {
      case Fetched(status) if status == 200 => {
        // extract the urls to be explored
        val (runWithPendingFetches, urlsToFetch) =
          runWithResponse.withNewUrlsToBeExplored(httpResponse.extractedURLs).takeAtMost(Strategy.maxUrlsToFetch)
        // extract the calls to the assertor to be made
        val assertorCalls =
          if (httpResponse.method === GET) {
            val assertors = strategy.getAssertors(httpResponse)
            assertors map { assertor => AssertorCall(runId, assertor, httpResponse) }
          } else {
            Set.empty[AssertorCall]
          }
        val runWithPendingAssertorCalls =
          runWithPendingFetches.copy(pendingAssertorCalls = runWithPendingFetches.pendingAssertorCalls ++ assertorCalls.map(ac => (ac.assertor.id, ac.response.url) -> ac))
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
      case response => {
        logger.warn("Ignoring: " + response)
        val (runWithPendingFetches, urlsToFetch) =
          runWithResponse.takeAtMost(Strategy.maxUrlsToFetch)
        (runWithPendingFetches, urlsToFetch, List.empty)
      }
    }
  }

  /** fix because of CSS Validator. See http://www.w3.org/mid/511910CB.6050608@w3.org
    */
  def fixedAssertorResult(result: AssertorResult): AssertorResult = {
    import LocalValidators.CSSValidator.{ id => cssValId }
    if (result.assertor === LocalValidators.CSSValidator.id) {
      // we don't want the assertions that were already received
      val filteredAssertions =
        result.assertions filterNot { case (url, _) =>
          val alreadyReceived =
            assertions.get(url).map(_.isDefinedAt(cssValId)).getOrElse(false)
          alreadyReceived
        }
      result.copy(assertions = filteredAssertions)
    } else {
      result
    }
  }

  private def withAssertorResult(result: AssertorResult, timestamp: DateTime): Run = {

    import result.{ assertor => assertorId }

    // could be optimized
    val (globalNbErrors, globalNbWarnings) = Assertion.countErrorsAndWarnings(result.assertions.values.flatten)

    // accumulate assertions and take care of the keys
    var newAssertions = this.assertions
    var newResourceDatas = this.resourceDatas
    var newGroupedAssertionDatas = this.groupedAssertionDatas

    result.assertions foreach { case (url, assertions) =>
      val (nbErrors, nbWarnings) = Assertion.countErrorsAndWarnings(assertions)
      // Assertion
      this.assertions.get(url) match {
        case None => newAssertions += (url -> Map(assertorId -> assertions))
        case Some(existingAAMap) =>
          existingAAMap.get(assertorId) match {
            case None =>
              newAssertions += (url -> (existingAAMap + (assertorId -> assertions)))
            case Some(_) =>
              logger.warn(s"Got assertions for existing ($url, $assertorId). This should have been filtered and will be ignored.")
          }
      }
      // ResourceData
      val resourceData = newResourceDatas.get(url) match {
        case None =>
          ResourceData(url, timestamp, nbWarnings, nbErrors)
        case Some(ResourceData(_, _, w, e)) =>
          ResourceData(url, timestamp, w + nbWarnings, e + nbErrors)
      }
      newResourceDatas += (url -> resourceData)
      // GroupedAssertionData
      assertions foreach { assertion =>
        newGroupedAssertionDatas.get(assertion.id) match {
          case None =>
            newGroupedAssertionDatas += (assertion.id -> GroupedAssertionData(assertion))
          case Some(gad) =>
            newGroupedAssertionDatas += (assertion.id -> (gad + assertion))
        }
      }
    }

    val newRun = this.copy(
      assertions = newAssertions,
      resourceDatas = newResourceDatas,
      groupedAssertionDatas = newGroupedAssertionDatas,
      errors = this.errors + globalNbErrors,
      warnings = this.warnings + globalNbWarnings,
      pendingAssertorCalls = pendingAssertorCalls - ((result.assertor, result.sourceUrl)),
      assertorResponsesReceived = assertorResponsesReceived + 1)

    newRun
  }

  private def withAssertorFailure(fail: AssertorFailure): Run = {
    this.copy(
      pendingAssertorCalls = pendingAssertorCalls - ((fail.assertor, fail.sourceUrl)),
      assertorResponsesReceived = assertorResponsesReceived + 1
    )
  }

}

