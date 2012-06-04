package org.w3.vs.model

import org.scalatest._
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.w3.banana.jena._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL

class BindersTest extends WordSpec with MustMatchers {

  val binders = Binders(JenaOperations, JenaGraphUnion, JenaGraphTraversal)
  import binders._

  def testSerializeDeserialize[T](binder: PointedGraphBinder[Jena, T])(t: T) = {
    import binder._
    fromPointedGraph(toPointedGraph(t)) must be === (Success(t))
  }

  "OrganizationVO" in {
    testSerializeDeserialize(OrganizationVOBinder) {
      OrganizationVO(name = "foo", admin = UserId())
    }
  }

  "JobVO" in {
    testSerializeDeserialize(JobVOBinder) {
      JobVO(
        name = "foo",
        createdOn = DateTime.now(DateTimeZone.UTC),
        creatorId = UserId(),
        organizationId = OrganizationId(),
        strategyId = StrategyId())
    }
  }

  "AssertionVO no description" in {
    testSerializeDeserialize(AssertionVOBinder) {
      AssertionVO(
        jobId = JobId(),
        runId = RunId(),
        assertorId = AssertorId(),
        url = URL("http://example.com/foo"),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None,
        timestamp = DateTime.now(DateTimeZone.UTC))
    }
  }

  "AssertionVO with description" in {
    testSerializeDeserialize(AssertionVOBinder) {
      AssertionVO(
        jobId = JobId(),
        runId = RunId(),
        assertorId = AssertorId(),
        url = URL("http://example.com/foo"),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = Some("some desc"),
        timestamp = DateTime.now(DateTimeZone.UTC))
    }
  }

  "ContextVO" in {
    testSerializeDeserialize(ContextVOBinder) {
      ContextVO(
        content = "foo",
        line = Some(42),
        column = None,
        assertionId = AssertionId())
    }
  }

  /*"AssertorResultVO" in {
    testSerializeDeserialize(AssertorResultVOBinder) {
      AssertorResultVO(
        jobId = JobId(),
        runId = RunId(),
        assertorId = AssertorId(),
        sourceUrl = URL("http://example.com/foo"),
        timestamp = DateTime.now(DateTimeZone.UTC))

    }
  }*/

  "JobDataVO" in {
    testSerializeDeserialize(JobDataVOBinder) {
      JobDataVO(
        jobId = JobId(),
        resources = 42,
        errors = 43,
        warnings = 44,
        timestamp = DateTime.now(DateTimeZone.UTC))
    }
  }

  val errorResponseVO =
    ErrorResponseVO(
      jobId = JobId(),
      runId = RunId(),
      url = URL("http://example.com/foo"),
      action = GET,
      timestamp = DateTime.now(DateTimeZone.UTC),
      why = "just because")

  "ErrorResponseVO" in {
    testSerializeDeserialize(ErrorResponseVOBinder)(errorResponseVO)
  }

  val httpResponseVO =
    HttpResponseVO(
      jobId = JobId(),
      runId = RunId(),
      url = URL("http://example.com/foo"),
      action = GET,
      timestamp = DateTime.now(DateTimeZone.UTC),
      status = 200,
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")))

  "HttpResponseVO" in {
    testSerializeDeserialize(HttpResponseVOBinder)(httpResponseVO)
  }

  "ResourceResponseVO as ErrorResponseVO" in {
    testSerializeDeserialize(ResourceResponseVOBinder)(errorResponseVO)
  }

  "ResourceResponseVO as HttpResponseVO" in {
    testSerializeDeserialize(ResourceResponseVOBinder)(httpResponseVO)
  }

  "RunVO" in {
    testSerializeDeserialize(RunVOBinder) {
      RunVO(
        toBeExplored = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")),
        fetched = Set(URL("http://example.com/foo"), URL("http://example.com/bar")),
        createdAt = DateTime.now(DateTimeZone.UTC),
        jobId = JobId(),
        jobDataId = JobDataId())
    }
  }

  "StrategyVO" in {
    testSerializeDeserialize(StrategyVOBinder) {
      StrategyVO(
        entrypoint = URL("http://example.com/foo"),
        distance = 2,
        linkCheck = true,
        maxResources = 100,
        filter = Filter.includeEverything)
    }
  }


  "UserVO" in {
    testSerializeDeserialize(UserVOBinder) {
      UserVO(
        name = "foo bar",
        email = "foo@example.com",
        password = "secret",
        organizationId = OrganizationId())
    }
  }


}
