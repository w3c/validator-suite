package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation
import org.w3.banana._

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
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC))(implicit conf: VSConfiguration) {
  
  //def getAssertorResult: FutureVal[Exception, AssertorResult] = AssertorResult.get(assertorResponseId)
  def getContexts: FutureVal[Exception, Iterable[Context]] = Context.getForAssertion(id) 

  def toValueObject: AssertionVO = AssertionVO(id, jobId, runId, assertorId, url, lang, title, severity, description, timestamp)

  def save(): FutureVal[Exception, Unit] = Assertion.save(this)

}

object Assertion {

  def apply(vo: AssertionVO)(implicit conf: VSConfiguration): Assertion = {
    import vo._
    Assertion(id, jobId, runId, assertorId, url, lang, title, severity, description, timestamp)
  }

  def getAssertionVO(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, AssertionVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = AssertionUri(id)
    FutureVal(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, AssertionVO]{
        val pointed = PointedGraph(uri, graph)
        AssertionVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }

  def get(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Assertion] =
    getAssertionVO(id) map (Assertion(_))

  def getForJob(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(Iterable())
  }

  def getForRun(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] = sys.error("ni")

  def saveAssertionVO(vo: AssertionVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = AssertionVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(AssertionUri(vo.id), graph)
    FutureVal.toFutureValException(FutureVal.applyTo(result))
  }

  def save(assertion: Assertion)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveAssertionVO(assertion.toValueObject)

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


