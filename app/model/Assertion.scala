package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

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
  
  //def getAssertorResponse: FutureVal[Exception, AssertorResponse] = AssertorResonse.getResponseWith()
  def getContexts: FutureVal[Exception, Iterable[Context]] = Context.getForAssertion(id) 
  
}

case class AssertionClosed(assertion: Assertion, contexts: Iterable[Context])

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
  def getForAssertion(id: AssertionId): FutureVal[Exception, Iterable[Context]] = sys.error("ni")

}
