package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

case class Assertion(
    id: AssertionId = AssertionId(),
    url: URL,
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    assertorResponseId: AssertorResponseId) {
  
  //def getAssertorResponse: FutureVal[Exception, AssertorResponse] = AssertorResonse.getResponseWith()
  def getContexts: FutureVal[Exception, Iterable[Context]] = Context.getForAssertion(id) 
  def toValueObject: AssertionVO = AssertionVO(id, url, lang, title, severity, description, assertorResponseId)
}

object Assertion {
  def get(id: AssertionId): FutureVal[Exception, Assertion] = sys.error("ni")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")
  def getForResponse(id: AssertorResponseId): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")
}

case class AssertionClosed(assertion: Assertion, contexts: Iterable[Context])


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


case class Context(
    id: ContextId = ContextId(),
    content: String,
    line: Option[Int], 
    column: Option[Int],
    assertionId: AssertionId) {
  
  def getAssertion: FutureVal[Exception, Assertion] = sys.error("ni")
  def toValueObject: ContextVO = ContextVO(id, content, line, column, assertionId)
} 

object Context {
  def get(id: ContextId): FutureVal[Exception, Context] = sys.error("ni")
  def getForAssertion(id: AssertionId): FutureVal[Exception, Iterable[Context]] = sys.error("ni")
}
