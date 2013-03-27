package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import scalaz.Scalaz._
import scalaz._
import org.w3.vs._

case class Assertion(
    //id: AssertionTypeId,
    url: URL,
    assertor: AssertorId,
    contexts: Vector[Context],
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC)) {

  def occurrences = scala.math.max(1, contexts.size)

  val id: AssertionTypeId = AssertionTypeId(assertor, title)

  override def toString: String =
    s"""Assertion($url, $assertor, $severity)"""

}

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
