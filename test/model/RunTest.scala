package org.w3.vs.model

import org.w3.util._
import org.w3.vs.util._
import org.scalatest._
import org.scalatest.matchers._
import org.joda.time.{ DateTime, DateTimeZone }

class RunTest extends WordSpec with MustMatchers {

  val strategy =
    Strategy(
      entrypoint = URL("http://w3.org/"),
      linkCheck = true,
      maxResources = 100,
      filter = Filter(include = Everything, exclude = Nothing),
      assertorsConfiguration = Map.empty)

  "A fresh run" must {
    val fresh: Run = Run.freshRun(UserId(), JobId(), strategy)
    "be really fresh" in {
      fresh.completedOn must be('empty)
      fresh.pending must be('empty)
      fresh.knownResources must be('empty)
    }
  }

  "A Run must filter out already seen assertions" in {
    val assertorId = AssertorId("test_assertor")
    val assertion =
      Assertion(
        url = URL("http://example.com/foo"),
        assertor = assertorId,
        contexts = List(Context(content = "foo", line = Some(42), column = None), Context(content = "bar", line = None, column = Some(2719))),
        lang = "fr",
        title = "bar",
        severity = Warning,
        description = None,
        timestamp = DateTime.now(DateTimeZone.UTC))
    val ar = AssertorResult(Run.Context(UserId(), JobId(), RunId()), assertorId, URL("http://example.com"), List(assertion))
    val run = Run.freshRun(UserId(), JobId(), strategy)
    run.withAssertorResult(ar)._1.withAssertorResult(ar)._1 must be(run.withAssertorResult(ar)._1)
  }

}

