package org.w3.vs.model

import org.w3.util._
import org.joda.time._

sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val runId: RunId
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}

case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: Throwable) extends AssertorResponse

case class AssertorResult(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    url: URL,
    assertions: Iterable[AssertionClosed]) extends AssertorResponse {
  
  lazy val errors = assertions.filter(_.assertion.severity == Error).foldLeft(0){(count, assertionClosed) => count + scala.math.max(1, assertionClosed.contexts.size)}
  lazy val warnings = assertions.filter(_.assertion.severity == Warning).foldLeft(0){(count, assertionClosed) => count + scala.math.max(1, assertionClosed.contexts.size)}
  lazy val isValid = ! (assertions.exists(_.assertion.severity == Error) || assertions.exists(_.assertion.severity == Warning)) 
  
}
