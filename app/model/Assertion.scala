package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val runId: RunId
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}

// not a persisted object for now
case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResponse

/** 
 *  AssertorResult 
 */
case class AssertorResultVO(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now)

// closed with number of errors and warnings
case class AssertorResult(
    valueObject: AssertorResultVO,
    errors: Int,
    warnings: Int) extends AssertorResponse {
  
  def id = valueObject.id
  def sourceUrl = valueObject.sourceUrl
  def timestamp = valueObject.timestamp
  
  //def isValid = ! hasError
  //def hasError: Boolean = assertions exists {_.severity == Error}
  //def hasWarnings: Boolean = assertions exists {_.severity == Warning}
  //def numberOfErrors = assertions.filter(_.severity == Error).size
  //def numberOfWarnings = assertions.filter(_.severity == Warning).size
  
  def getJob: FutureVal[Exception, Job] = sys.error("ni")
  def getRun: FutureVal[Exception, Run] = sys.error("ni")
  def getAssertor: FutureVal[Exception, Assertor] = sys.error("ni")
  def getAssertions: FutureVal[Exception, Iterable[Assertion]] = Assertion.getForResponse(id)
  def save(): FutureVal[Exception, AssertorResult] = AssertorResult.save(this)
}

object AssertorResult {
  
  def apply(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now,
    errors: Int,
    warnings: Int) = new AssertorResult(AssertorResultVO(id, jobId, runId, assertorId, sourceUrl, timestamp), errors, warnings)
  
  def get(id: AssertorResponseId): FutureVal[Exception, AssertorResult] = sys.error("ni")
  def getForJob(id: JobId, after: Option[DateTime] = None): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")
  def getForURL(url: URL): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")
  def save(result: AssertorResult): FutureVal[Exception, AssertorResult] = sys.error("ni")
}

/** 
 *  Assertion
 *  
 *  @param severity a severity (either: error, warning, info)
 *  @param id identifier that defines uniquely this kind of event
 *  @param lang XML lang code
 *  @param contexts a sequence of [[org.w3.vs.validator.Context]]s
 */
case class AssertionVO(
    id: AssertionId = AssertionId(),
    url: URL,
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    assertorResponseId: AssertorResponseId)

case class Assertion(valueObject: AssertionVO) {
  
  def id: AssertionId = valueObject.id
  def url: URL = valueObject.url
  def lang: String = valueObject.lang
  def title: String = valueObject.title
  def severity: AssertionSeverity = valueObject.severity
  def description: Option[String] = valueObject.description
  
  def getAssertorResponse: FutureVal[Exception, AssertorResponse]
  def getContexts: FutureVal[Exception, Seq[Context]] 
  
}

object Assertion {
  
  def apply(
      id: AssertionId = AssertionId(),
      url: URL,
      lang: String,
      title: String,
      severity: AssertionSeverity,
      description: Option[String],
      assertorResponseId: AssertorResponseId): Assertion =
    Assertion(AssertionVO(id, url, lang, title, severity, description, assertorResponseId))
  
  def get(id: AssertionId): FutureVal[Exception, Assertion] = sys.error("ni")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")
  def getForResponse(id: AssertorResponseId): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")
  
}

sealed trait AssertionSeverity
case object Error extends AssertionSeverity
case object Warning extends AssertionSeverity
case object Info extends AssertionSeverity

object AssertionSeverity {
  
  def apply(severity: String): AssertionSeverity = {
    severity.toLowerCase.trim match {
      case "error" => Error
      case "warning" => Warning
      case "info" => Info
      case _ => Info // TODO log
    }
  }
  
}

/** 
 *  Context
 *  
 *  @param content a code snippet from the source
 *  @param line an optional line in the source
 *  @param column an optional column in the source
 */
case class ContextVO(
    id: ContextId = ContextId(),
    content: String,
    line: Option[Int], 
    column: Option[Int],
    assertionId: AssertionId)

case class Context(valueObject: ContextVO) {
  
  def id: ContextId = valueObject.id
  def content: String = valueObject.content
  def line: Option[Int] = valueObject.line
  def column: Option[Int] = valueObject.column
  
  def getAssertion: FutureVal[Exception, Assertion] = sys.error("ni")
  
} 

object Context {
  
  def apply(
      content: String,
      line: Option[Int], 
      column: Option[Int],
      assertionId: AssertionId): Context = 
   Context(ContextVO(content = content, line = line, column = column, assertionId = assertionId))
  
  def get(id: ContextId): FutureVal[Exception, Context] = sys.error("ni")
  def getForAssertion(id: AssertionId): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")

}
