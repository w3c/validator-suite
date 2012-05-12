package org.w3.vs.model

import org.w3.util.URL
import org.joda.time._
import java.util.UUID
import org.w3.vs.assertor._
import scalaz.Validation

sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val assertorId: AssertorId
  val url: URL
  val timestamp: DateTime
}

case class AssertorResult(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    assertorId: AssertorId,
    url: URL,
    timestamp: DateTime = DateTime.now,
    assertions: Iterable[Assertion]) extends AssertorResponse {
  
  def isValid = if (hasError) false else true
  
  def hasError: Boolean = assertions exists { _.isError }
  def hasWarnings: Boolean = assertions exists { _.isWarning }
  
  def numberOfErrors = assertions.view.filter(_.isError).size
  def numberOfWarnings = assertions.view.filter(_.isWarning).size
  
}

case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    assertorId: AssertorId,
    url: URL,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResponse


/** An event coming from an observation
 * 
 *  @param severity a severity (either: error, warning, info)
 *  @param id identifier that defines uniquely this kind of event
 *  @param lang XML lang code
 *  @param contexts a sequence of [[org.w3.vs.validator.Context]]s
 */
case class Assertion(severity: String, assertId: String, lang: String, contexts: Seq[Context], title: String, description: Option[String]) {
  def isError = severity == "error"
  def isWarning = severity == "warning"
  def isInfo = severity == "info"
}

/** A context for an [[org.w3.vs.validator.Event]]
 *
 *  @param content a code snippet from the source
 *  @param line an optional line in the source
 *  @param column an optional column in the source
 */
case class Context(content:String, line:Option[Int], column:Option[Int]) // TODO remove ref


