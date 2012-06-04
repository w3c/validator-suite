package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

case class Assertion(
    id: AssertionId = AssertionId(),
    jobId: JobId,
    runId: RunId,
    assertorId: AssertorId,
    url: URL,
    lang: String,
    title: String,
    severity: AssertionSeverity,
    description: Option[String],
    timestamp: DateTime = DateTime.now)(implicit conf: VSConfiguration) {
  
  //def getAssertorResult: FutureVal[Exception, AssertorResult] = AssertorResult.get(assertorResponseId)
  def getContexts: FutureVal[Exception, Iterable[Context]] = Context.getForAssertion(id) 
  def toValueObject: AssertionVO = AssertionVO(id, jobId, runId, assertorId, url, lang, title, severity, description, timestamp)
}

object Assertion {
  def get(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Assertion] = sys.error("ni")
  def getForJob(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] = {
    
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(Iterable(
      Assertion.apply(AssertionId(), JobId(), RunId(), AssertorId(), 
          URL("http://www.w3.org"), "en", "T", Error, None),
      Assertion.apply(AssertionId(), JobId(), RunId(), AssertorId(), 
          URL("http://www.w3.org"), "en", "T", Warning, None),
      Assertion.apply(AssertionId(), JobId(), RunId(), AssertorId(), 
          URL("http://www.w3.org/TR"), "en", "T", Warning, None)
    ))
    
  }
  def getForRun(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")
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
    assertionId: AssertionId)(implicit conf: VSConfiguration) {
  
  def getAssertion: FutureVal[Exception, Assertion] = Assertion.get(assertionId)
  def toValueObject: ContextVO = ContextVO(id, content, line, column, assertionId)
} 

object Context {
  
  def apply(
      content: String, 
      line: Option[Int], 
      column: Option[Int], 
      assertionId: AssertionId)(implicit conf: VSConfiguration): Context =
    Context(ContextId(), content, line, column, assertionId)
  
  def get(id: ContextId)(implicit conf: VSConfiguration): FutureVal[Exception, Context] = sys.error("ni")
  def getForAssertion(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Context]] = sys.error("ni")
}
