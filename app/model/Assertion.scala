package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.banana._
import scalaz.Scalaz._
import scalaz._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.banana.util._

case class Assertion(
    url: URL,
    assertor: String,
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
