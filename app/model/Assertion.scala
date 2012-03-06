package org.w3.vs.model

import org.w3.util.URL
import org.joda.time._
import java.util.UUID
import org.w3.vs.assertor._
import scalaz.Validation

sealed trait AssertorResult {
  type Id = UUID
  
  val id: Id
  // the urls that were needed to generate this assertion
  val url: URL
  // the id of the assertor that generated this assertion
  val assertorId: AssertorId
  // an assertion was produced in the context of a run
  val jobId: Job#Id
  // when this assertion was produced
  val timestamp: DateTime
}


case class Assertions(
    id: AssertorResult#Id = UUID.randomUUID(),
    url: URL,
    assertorId: AssertorId,
    jobId: Job#Id,
    timestamp: DateTime = new DateTime,
    assertions: Iterable[RawAssertion]) extends AssertorResult {
  
  def hasError: Boolean = assertions exists { _.isError }
  def hasWarnings: Boolean = assertions exists { _.isWarning }
  
  def numberOfOks = if (hasError || hasWarnings) 0 else 1
  
  def numberOfErrors = assertions.view.filter(_.isError).size
  
  def numberOfWarnings = assertions.view.filter(_.isWarning).size
  
}

case class AssertorFail(
    id: AssertorResult#Id = UUID.randomUUID(),
    url: URL,
    assertorId: AssertorId,
    jobId: Job#Id,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResult

