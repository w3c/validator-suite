package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation


sealed trait AssertorResponse {
  val id: AssertorResponseId
  val jobId: JobId
  val assertorId: AssertorId
  val sourceUrl: URL
  val timestamp: DateTime
}

// not a persisted object for now
case class AssertorFailure(
    id: AssertorResponseId = AssertorResponseId(),
    jobId: JobId,
    assertorId: AssertorId,
    sourceUrl: URL,
    timestamp: DateTime = new DateTime,
    why: String) extends AssertorResponse

/** 
 *  AssertorResult 
 */
case class AssertorResultVO(
    id: AssertorResponseId = AssertorResponseId(),
    sourceUrl: URL,
    timestamp: DateTime = DateTime.now,
    jobId: JobId,
    assertorId: AssertorId) {}

case class AssertorResult(valueObject: AssertorResultVO) extends AssertorResponse {
  
  def id = valueObject.id
  def sourceUrl = valueObject.sourceUrl
  def timestamp = valueObject.timestamp
  
  def getJob: FutureVal[Exception, Job]
  def getAssertor: FutureVal[Exception, Assertor]
  
  //def isValid = ! hasError
  //def hasError: Boolean = assertions exists {_.severity == Error}
  //def hasWarnings: Boolean = assertions exists {_.severity == Warning}
  //def numberOfErrors = assertions.filter(_.severity == Error).size
  //def numberOfWarnings = assertions.filter(_.severity == Warning).size
  
  def getAssertions: FutureVal[Exception, Iterable[Assertion]] = {
    Assertion.get(AssertorResponseId)
  }
  
}

object AssertorResult {
  
  def get(id: AssertorResponseId): AssertorResult = sys.error("ni")
  def getForJob(id: JobId, after: Option[DateTime] = None): Iterable[AssertorResult] = sys.error("ni")
  def getForURL(url: URL): Iterable[AssertorResult] = sys.error("ni")
  
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
  
  def get(id: AssertionId): Assertion = sys.error("ni")
  def getForJob(id: JobId): Iterable[Assertion] = sys.error("ni")
  def getForResponse(id: AssertorResponseId): Iterable[Assertion] = sys.error("ni")
  
}

sealed trait AssertionSeverity
case object Error extends AssertionSeverity
case object Warning extends AssertionSeverity
case object Info extends AssertionSeverity

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
  
  def getAssertion: FutureVal[Exception, Assertion]
  
} 

object Context {
  
  def get(id: ContextId): FutureVal[Exception, Context] = sys.error("ni")
  def getForAssertion(id: AssertionId): FutureVal[Exception, Iterable[AssertorResult]] = sys.error("ni")

}
