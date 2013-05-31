package org.w3.vs.store

import org.scalatest.{ Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs.util.URL
import org.w3.vs.util.html.Doctype
import org.w3.vs.model._
import Formats._

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
    creatorId = UserId(),
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
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")), Some(Doctype("html", "", "")))

  val assertorResult =
    AssertorResult(AssertorId("test_assertor"), URL("http://example.com"), Map(URL("http://example.com") -> Vector(assertion)))

  val assertorFailure =
    AssertorFailure(AssertorId("test_assertor"), URL("http://example.com"), "parceke")

  val httpResponseEvent = ResourceResponseEvent(UserId(), JobId(), RunId(), httpResponse)
  val errorResponseEvent = ResourceResponseEvent(UserId(), JobId(), RunId(), errorResponse)
  val cancelRunEvent = DoneRunEvent(UserId(), JobId(), RunId(), Cancelled, runData.resources, runData.errors, runData.warnings, Map(resourceData.url -> resourceData, resourceData2.url -> resourceData2), Vector(groupedAssertionData))
  val completeRunEvent = DoneRunEvent(UserId(), JobId(), RunId(), Completed, runData.resources, runData.errors, runData.warnings, Map(resourceData.url -> resourceData, resourceData2.url -> resourceData2), Vector(groupedAssertionData))

  val user = User.create(
    name = "foo bar",
    email = "foo@example.com",
    password = "secret",
    isSubscriber = true)

  val userId = UserId()
  
  val userJobId = (UserId(), JobId())
  
  val userJobRunId = (UserId(), JobId(), RunId())

  val jobData =
    JobData(JobId(), "foo", URL("http://example.com"), JobDataRunning(2719), Some(DateTime.now(DateTimeZone.UTC)), 1, 2, 3, 4, 5)

  "all domain must be serializable and then deserializable" in {
    toJson(strategy).as[Strategy] must be(strategy)
    toJson(jobStatus).as[JobStatus] must be(jobStatus)
    toJson(resourceData).as[ResourceData] must be(resourceData)
    toJson(runData).as[RunData] must be(runData)
    toJson(done).as[JobStatus] must be(done)
    toJson(NeverStarted)(NeverStartedFormat).as[JobStatus] must be(NeverStarted)
    toJson(Zombie)(ZombieFormat).as[JobStatus] must be(Zombie)
    toJson(job).as[Job] must be(job)
    toJson(context).as[Context] must be(context)
    toJson(Error).as[AssertionSeverity] must be(Error)
    toJson(assertion).as[Assertion] must be(assertion)
    toJson(assertionTypeId).as[AssertionTypeId] must be(assertionTypeId)
    toJson(groupedAssertionData).as[GroupedAssertionData] must be(groupedAssertionData)
    toJson(GET).as[HttpMethod] must be(GET)
    toJson(errorResponse).as[ResourceResponse] must be(errorResponse)
    toJson(httpResponse).as[ResourceResponse] must be(httpResponse)
    toJson(assertorResult).as[AssertorResponse] must be(assertorResult)
    toJson(assertorFailure).as[AssertorResponse] must be(assertorFailure)
    toJson(httpResponseEvent).as[RunEvent] must be(httpResponseEvent)
    toJson(errorResponseEvent).as[RunEvent] must be(errorResponseEvent)
    toJson(cancelRunEvent).as[RunEvent] must be(cancelRunEvent)
    toJson(completeRunEvent).as[RunEvent] must be(completeRunEvent)
    toJson(user).as[User] must be(user)
    toJson(userJobId).as[(UserId, JobId)] must be(userJobId)
    toJson(userJobRunId).as[(UserId, JobId, RunId)] must be(userJobRunId)
    toJson(jobData).as[JobData] must be(jobData)
  }

  

}
