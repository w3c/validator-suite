package org.w3.vs.store

import org.scalatest.{ Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL
import org.w3.vs.model._
import org.w3.vs._
import scala.util._
import org.w3.vs.actor.JobActor._
import Formats._

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

class FormatsTest extends WordSpec with MustMatchers {

  val strategy =
    Strategy(
      entrypoint = URL("http://example.com/foo"),
      linkCheck = true,
      maxResources = 100,
      filter = Filter.includeEverything,
      assertorsConfiguration = AssertorsConfiguration.default)

  val jobVo= JobVO(
    name = "foo",
    createdOn = DateTime.now(DateTimeZone.UTC),
    strategy = strategy,
    creator = UserId())

  val context = Context(content = "foo", line = Some(42), column = None)

  val assertion =
    Assertion(
      url = URL("http://example.com/foo"),
      assertor = AssertorId("test_assertor"),
      contexts = List(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
      lang = "fr",
      title = "bar",
      severity = Warning,
      description = None,
      timestamp = DateTime.now(DateTimeZone.UTC))

  val errorResponse =
    ErrorResponse(
      url = URL("http://example.com/foo"),
      method = GET,
      why = "just because")

  val httpResponse =
    HttpResponse(
      url = URL("http://example.com/foo"),
      method = GET,
      status = 200,
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")))

  val assertorResult =
    AssertorResult((UserId(), JobId(), RunId()), AssertorId("test_assertor"), URL("http://example.com"), List(assertion))

  val assertorFailure =
    AssertorFailure((UserId(), JobId(), RunId()), AssertorId("test_assertor"), URL("http://example.com"), "parceke")

  val httpResponseEvent = ResourceResponseEvent(httpResponse)
  val errorResponseEvent = ResourceResponseEvent(errorResponse)
  val beProactiveEvent = BeProactiveEvent()
  val beLazyEvent = BeLazyEvent()

  val userVo = UserVO(
    name = "foo bar",
    email = "foo@example.com",
    password = "secret")

  val userId = UserId()
  
  val userJobId = (UserId(), JobId())
  
  val userJobRunId = (UserId(), JobId(), RunId())

  "all domain must be serializable and then deserializable" in {
    toJson(BeProactive).as[BeProactive.type] must be(BeProactive)
    toJson(BeLazy).as[BeLazy.type] must be(BeLazy)
    toJson(strategy).as[Strategy] must be(strategy)
    toJson(jobVo).as[JobVO] must be(jobVo)
    toJson(context).as[Context] must be(context)
    toJson(Error).as[AssertionSeverity] must be(Error)
    toJson(assertion).as[Assertion] must be(assertion)
    toJson(GET).as[HttpMethod] must be(GET)
    toJson(errorResponse).as[ResourceResponse] must be(errorResponse)
    toJson(httpResponse).as[ResourceResponse] must be(httpResponse)
    toJson(assertorResult).as[AssertorResponse] must be(assertorResult)
    toJson(assertorFailure).as[AssertorResponse] must be(assertorFailure)
    toJson(httpResponseEvent).as[RunEvent] must be(httpResponseEvent)
    toJson(errorResponseEvent).as[RunEvent] must be(errorResponseEvent)
    toJson(beProactiveEvent).as[RunEvent] must be(beProactiveEvent)
    toJson(beLazyEvent).as[RunEvent] must be(beLazyEvent)
    toJson(userVo).as[UserVO] must be(userVo)
    toJson(userId).as[UserId] must be(userId)
    toJson(userJobId).as[(UserId, JobId)] must be(userJobId)
    toJson(userJobRunId).as[(UserId, JobId, RunId)] must be(userJobRunId)
  }

  

}
