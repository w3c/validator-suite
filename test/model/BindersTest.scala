package org.w3.vs.store

import org.scalatest.{Filter => ScalaTestFilter, _}
import org.scalatest.matchers._
import scalaz._
import org.w3.banana._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.URL
import org.w3.vs.model._
import Binders._
import org.w3.vs._
import org.w3.vs.diesel._

class BindersTest extends WordSpec with MustMatchers {

  def testSerializeDeserialize[T](binder: PointedGraphBinder[Rdf, T])(t: T) = {
    import binder._
    val pointed = toPointedGraph(t)
    val result = fromPointedGraph(pointed)
    result match {
      case Failure(throwable) => {
        println("1. "+t)
        println("2. === ")
        println(pointed.graph)
        println("===")
        println("3. "+result)
//        throwable.printStackTrace()
      }
      case _ => ()
    }
    result must be === (Success(t))
  }

  "OrganizationVO" in {
    testSerializeDeserialize(OrganizationVOBinder) {
      OrganizationVO(name = "foo", admin = UserId())
    }
  }

  "StrategyVO" in {
    testSerializeDeserialize(StrategyBinder) {
      Strategy(
        entrypoint = URL("http://example.com/foo"),
        linkCheck = true,
        maxResources = 100,
        filter = Filter.includeEverything,
        assertorSelector = AssertorSelector("custom-assertor-selector", Map("nawak" -> List("cssval", "valnu"))))
    }
  }

  val strategy =
    Strategy(
      entrypoint = URL("http://example.com/foo"),
      linkCheck = true,
      maxResources = 100,
      filter = Filter.includeEverything,
      assertorSelector = AssertorSelector("custom-assertor-selector", Map("nawak" -> List("cssval", "valnu"))))

  "JobVO" in {
    testSerializeDeserialize(JobVOBinder) {
      JobVO(
        name = "foo",
        createdOn = DateTime.now(DateTimeZone.UTC),
        strategy = strategy,
        creator = UserId(),
        organization = OrganizationId())
    }
  }

  "Assertion" in {
    val assertion =
      Assertion(
        url = URL("http://example.com/foo"),
        assertorId = AssertorId(),
        contexts = List(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None,
        timestamp = DateTime.now(DateTimeZone.UTC))
    assertion.toPG.as[Assertion] must be(Success(assertion))
  }


  val errorResponse =
    ErrorResponse(
      url = URL("http://example.com/foo"),
      action = GET,
      timestamp = DateTime.now(DateTimeZone.UTC),
      why = "just because")

  "ErrorResponse" in {
    errorResponse.toPG.as[ErrorResponse] must be(Success(errorResponse))
  }

  val httpResponse =
    HttpResponse(
      url = URL("http://example.com/foo"),
      action = GET,
      timestamp = DateTime.now(DateTimeZone.UTC),
      status = 200,
      headers = Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz")),
      extractedURLs = List(URL("http://example.com/foo"), URL("http://example.com/foo"), URL("http://example.com/bar")))

  "HttpResponse" in {
    httpResponse.toPG.as[HttpResponse] must be(Success(httpResponse))
  }

  "ResourceResponse as ErrorResponse" in {
    errorResponse.toPG.as[ResourceResponse] must be(Success(errorResponse))
  }

  "ResourceResponse as HttpResponse" in {
    httpResponse.toPG.as[ResourceResponse] must be(Success(httpResponse))
  }

  "Run" in {
    val run = Run(id = (OrganizationId(), JobId(), RunId()), strategy = strategy)
    run.toPG.as[Run] must be(Success(run))
  }


  "UserVO" in {
    testSerializeDeserialize(UserVOBinder) {
      UserVO(
        name = "foo bar",
        email = "foo@example.com",
        password = "secret",
        organization = Some(OrganizationId()))
    }
  }

  "UserId" in {
    val userId = UserId()
    userId.toPG.as[UserId] must be(Success(userId))
  } 

  "OrganizationId" in {
    val orgId = OrganizationId()
    orgId.toPG.as[OrganizationId] must be(Success(orgId))
  } 

  "(OrganizationId, JobId)" in {
    val orgJobId = (OrganizationId(), JobId())
    orgJobId.toPG.as[(OrganizationId, JobId)] must be(Success(orgJobId))
  } 

  "(OrganizationId, JobId, RunId)" in {
    val orgJobRunId = (OrganizationId(), JobId(), RunId())
    orgJobRunId.toPG.as[(OrganizationId, JobId, RunId)] must be(Success(orgJobRunId))
  } 

}
