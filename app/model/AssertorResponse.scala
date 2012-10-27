package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import scalaz._
import scalaz.Scalaz._

sealed trait AssertorResponse {
  val context: (OrganizationId, JobId, RunId)
  val assertor: AssertorId
  val sourceUrl: URL
}
case class AssertorFailure(
    context: (OrganizationId, JobId, RunId),
    assertor: AssertorId,
    sourceUrl: URL,
    why: String) extends AssertorResponse

case class AssertorResult(
    context: (OrganizationId, JobId, RunId),
    assertor: AssertorId,
    sourceUrl: URL,
    assertions: List[Assertion]) extends AssertorResponse {
  
  lazy val (errors, warnings) = Assertion.countErrorsAndWarnings(assertions)

  lazy val isValid = (errors === 0) && (warnings === 0)
  
}
