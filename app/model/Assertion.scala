package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import scalaz.Scalaz._
import scalaz._
import org.w3.vs._

case class Assertion(
    url: URL,
    assertor: AssertorId,
    contexts: List[Context],
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC))

object Assertion {

  def countErrorsAndWarnings(assertions: Iterable[Assertion]): (Int, Int) = {
    var errors = 0
    var warnings = 0
    assertions foreach { assertion =>
      assertion.severity match {
        case Error => errors += math.max(1, assertion.contexts.size)
        case Warning => warnings += math.max(1, assertion.contexts.size)
        case Info => ()
      }
    }
    (errors, warnings)
  }

}
