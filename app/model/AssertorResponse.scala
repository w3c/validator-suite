package org.w3.vs.model

import org.w3.util._
import org.joda.time._

sealed trait AssertorResponse {
  val context: (OrganizationId, JobId, RunId)
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}
case class AssertorFailure(
    context: (OrganizationId, JobId, RunId),
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: Throwable) extends AssertorResponse

case class AssertorResult(
    context: (OrganizationId, JobId, RunId),
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    assertions: Iterable[Assertion]) extends AssertorResponse {
  
  lazy val errors =
    assertions.collect{ case a if a.severity == Error => scala.math.max(1, a.contexts.size) }.foldLeft(0)(_ + _)

  lazy val warnings =
    assertions.collect{ case a if a.severity == Warning => scala.math.max(1, a.contexts.size) }.foldLeft(0)(_ + _)

  lazy val isValid = ! assertions.exists(a => a.severity == Error || a.severity == Warning)
  
}
