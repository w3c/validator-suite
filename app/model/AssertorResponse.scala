package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import scalaz._
import scalaz.Scalaz._

sealed trait AssertorResponse {
  val runId: RunId
  val assertor: AssertorId
  val sourceUrl: URL
}
case class AssertorFailure(
    runId: RunId,
    assertor: AssertorId,
    sourceUrl: URL,
    why: String) extends AssertorResponse

case class AssertorResult(
    runId: RunId,
    assertor: AssertorId,
    sourceUrl: URL,
    assertions: List[Assertion]) extends AssertorResponse {
  
  val (errors, warnings) = Assertion.countErrorsAndWarnings(assertions)

  def isValid = (errors === 0) && (warnings === 0)
  
}
