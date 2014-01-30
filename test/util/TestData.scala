package org.w3.vs.util

import org.w3.vs.model._
import org.scalatest.{ Suite, BeforeAndAfterEach }
import org.w3.vs.web._
import org.w3.vs.ValidatorSuite

trait TestData {

  implicit def vs: ValidatorSuite

  object TestData {

    val strategy = Strategy(entrypoint = URL("http://localhost:9001/"), maxResources = 100)

    val user = User.create(email = "", name = "", password = "", isSubscriber = true, credits = 10000)
    val job = Job(name = "@@", strategy = strategy, creatorId = Some(user.id))

    val foo = URL("http://example.com/foo")
    val bar = URL("http://example.com/bar")

    val assertion1 =
      Assertion(
        id = AssertionTypeId(AssertorId("id1"), "bar"),
        url = foo,
        assertor = AssertorId("id1"),
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Error,
        description = None)

    val commonId = AssertionTypeId(AssertorId("id2"), "bar")

    val assertion2 =
      Assertion(
        id = commonId,
        url = foo,
        assertor = AssertorId("id2"),
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719)), Context(content = "baz", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None)

    val assertion3 =
      Assertion(
        id = commonId,
        url = bar,
        assertor = AssertorId("id2"),
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None)

    val httpResponse = HttpResponse(
      url = foo,
      method = GET,
      status = 200,
      headers = Headers(Map("Accept" -> List("foo"), "bar" -> List("baz", "bazz"))),
      extractedURLs = List(foo, foo, bar), Some(Doctype("html", "", "")))

    val ar1 = AssertorResult(AssertorId("id1"), foo, Map(foo -> Vector(assertion1)))
    val ar2 = AssertorResult(AssertorId("id2"), foo, Map(foo -> Vector(assertion2)))
    val ar3 = AssertorResult(AssertorId("id2"), foo, Map(bar -> Vector(assertion3)))

  }
}
