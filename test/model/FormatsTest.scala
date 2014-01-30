package org.w3.vs.store

import org.scalatest.{ Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs.web._
import org.w3.vs.model._
import Formats._
import org.w3.vs.util.VSTest
import org.w3.vs.ValidatorSuite
import play.api.Mode

// Play Json imports
import play.api.libs.json._
import Json.toJson

class FormatsTest extends VSTest {

  implicit val vs = new ValidatorSuite { val mode = Mode.Test }

  val strategy = Strategy(entrypoint = URL("http://example.com/foo"), maxResources = 100)

  val resourceData = ResourceData(URL("http://example.com/foo"), DateTime.now(DateTimeZone.UTC), 27, 19)
  val resourceData2 = ResourceData(URL("http://example.com/bar"), DateTime.now(DateTimeZone.UTC), 27, 19)

  val runData = RunData()

  val jobStatus = Running(RunId(), RunningActorName("foo"))

  val done = Done(RunId(), Completed, DateTime.now(DateTimeZone.UTC), runData)

  val job = Job(
    id = JobId(),
    name = "foo",
    createdOn = DateTime.now(DateTimeZone.UTC),
    strategy = strategy,
    creatorId = Some(UserId()),
    status = jobStatus,
    latestDone = Some(done))

  val context = Context(content = "foo", line = Some(42), column = None)

  val assertion =
    Assertion(
      id = AssertionTypeId("a"),
      url = URL("http://example.com/foo"),
      assertor = AssertorId("test_assertor"),
      contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
      lang = "fr",
      title = "bar",
      severity = Warning,
      description = None,
      timestamp = DateTime.now(DateTimeZone.UTC))

  val assertionTypeId = AssertionTypeId("unique-id")

  val groupedAssertionData =
    GroupedAssertionData(
      id = AssertionTypeId("unique-id"),
      assertor = AssertorId("test_assertor"),
      lang = "fr",
      title = "bar",
      severity = Warning,
      occurrences = 2,
      resources = Map(
        URL("http://example.com/foo") -> 1,
        URL("http://example.com/bar") -> 2
      )
    )

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
      headers = Headers(Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz"))),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")), Some(Doctype("html", "", "")))

  val assertorResult =
    AssertorResult(AssertorId("test_assertor"), URL("http://example.com"), Map(URL("http://example.com") -> Vector(assertion)))

  val assertorFailure =
    AssertorFailure(AssertorId("test_assertor"), URL("http://example.com"), "parceke")

  val httpResponseEvent = ResourceResponseEvent(Some(UserId()), JobId(), RunId(), httpResponse)
  val errorResponseEvent = ResourceResponseEvent(Some(UserId()), JobId(), RunId(), errorResponse)
  val cancelRunEvent = DoneRunEvent(Some(UserId()), JobId(), RunId(), Cancelled, runData.resources, runData.errors, runData.warnings, Map(resourceData.url -> resourceData, resourceData2.url -> resourceData2), Vector(groupedAssertionData))
  val completeRunEvent = DoneRunEvent(Some(UserId()), JobId(), RunId(), Completed, runData.resources, runData.errors, runData.warnings, Map(resourceData.url -> resourceData, resourceData2.url -> resourceData2), Vector(groupedAssertionData))

  val user = User.create(
    name = "foo bar",
    email = "foo@example.com",
    password = "secret",
    credits = 10000,
    isSubscriber = true)

  val userId = UserId()
  
  val userJobId = (UserId(), JobId())
  
  val userJobRunId = (UserId(), JobId(), RunId())

  val jobData =
    JobData(JobId(), "foo", URL("http://example.com"), JobDataRunning(2719), Some(DateTime.now(DateTimeZone.UTC)), 1, 2, 3, 4, 5)

  val coupon = Coupon(code = "CouponId", campaign = "CampaignId", description = Some("AT&T Coupon"), credits = 50, expirationDate = DateTime.now(DateTimeZone.UTC).plusDays(1))

  "all domain should be serializable and then deserializable" in {
    toJson(strategy).as[Strategy] should be(strategy)
    toJson(jobStatus).as[JobStatus] should be(jobStatus)
    toJson(resourceData).as[ResourceData] should be(resourceData)
    toJson(runData).as[RunData] should be(runData)
    toJson(done).as[JobStatus] should be(done)
    toJson(NeverStarted)(NeverStartedFormat).as[JobStatus] should be(NeverStarted)
    toJson(Zombie)(ZombieFormat).as[JobStatus] should be(Zombie)
    toJson(job).as[Job] should be(job)
    toJson(context).as[Context] should be(context)
    toJson(Error).as[AssertionSeverity] should be(Error)
    toJson(assertion).as[Assertion] should be(assertion)
    toJson(assertionTypeId).as[AssertionTypeId] should be(assertionTypeId)
    toJson(groupedAssertionData).as[GroupedAssertionData] should be(groupedAssertionData)
    toJson(GET).as[HttpMethod] should be(GET)
    toJson(errorResponse).as[ResourceResponse] should be(errorResponse)
    toJson(httpResponse).as[ResourceResponse] should be(httpResponse)
    toJson(assertorResult).as[AssertorResponse] should be(assertorResult)
    toJson(assertorFailure).as[AssertorResponse] should be(assertorFailure)
    toJson(httpResponseEvent).as[RunEvent] should be(httpResponseEvent)
    toJson(errorResponseEvent).as[RunEvent] should be(errorResponseEvent)
    toJson(cancelRunEvent).as[RunEvent] should be(cancelRunEvent)
    toJson(completeRunEvent).as[RunEvent] should be(completeRunEvent)
    toJson(user).as[User] should be(user)
    toJson(userJobId).as[(UserId, JobId)] should be(userJobId)
    toJson(userJobRunId).as[(UserId, JobId, RunId)] should be(userJobRunId)
    toJson(jobData).as[JobData] should be(jobData)
    toJson(coupon).as[Coupon] should be(coupon)
  }

  

}
