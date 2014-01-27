package org.w3.vs.model

import org.w3.vs.util._
import org.w3.vs.web._
import org.scalatest._
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }

class RunTest extends WordSpec with Matchers {

  val strategy =
    Strategy(entrypoint = URL("http://w3.org/"), maxResources = 100)

  "A fresh run" should {
    val fresh: Run = Run.freshRun(strategy)
    "be really fresh" in {
      fresh.completedOn should be('empty)
      fresh.pendingFetches should be('empty)
      fresh.knownResources should be('empty)
    }
  }

  "A Run should filter out already seen assertions" in {
    val assertorId = AssertorId("test_assertor")
    val assertion =
      Assertion(
        id = AssertionTypeId(assertorId, "bar"),
        url = URL("http://example.com/foo"),
        assertor = assertorId,
        contexts = Vector(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None,
        timestamp = DateTime.now(DateTimeZone.UTC))
    val ar = AssertorResult(assertorId, URL("http://example.com"), Map(URL("http://example.com") -> Vector(assertion)))
    val run = Run.freshRun(strategy)
    val event = AssertorResponseEvent(Some(UserId()), JobId(), run.runId, ar)
    run.step(event).run.step(event).run.assertions should be(run.step(event).run.assertions)
  }

}

