package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.store._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import org.w3.banana._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

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
  
  def delete(): FutureVal[Exception, Unit] = Assertion.delete(this)

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
  
  def fromPointedGraph(conf: VSConfiguration)(pointed: PointedGraph[conf.Rdf]): Validation[BananaException, Assertion] = {
    implicit val c = conf
    import conf.binders._
    for {
      vo <- AssertionVOBinder.fromPointedGraph(pointed)
    } yield {
      Assertion(vo)
    }
  }

  def fromGraph(conf: VSConfiguration)(graph: conf.Rdf#Graph): Validation[BananaException, Iterable[Assertion]] = {
    import conf.diesel._
    import conf.binders._
    val assertions: Iterable[Validation[BananaException, Assertion]] =
      graph.getAllInstancesOf(ont.Assertion) map { pointed => fromPointedGraph(conf)(pointed) }
    assertions.toList.sequence[({type l[X] = Validation[BananaException, X]})#l, Assertion]
  }

  // TODO
  def getForRun(runId: RunId, url: URL)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] =
    getForRun(runId) map { _.filter { _.url === url } }

  def getForRun(runId: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Assertion]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?assertionUri ?p ?o .
} WHERE {
  graph ?g {
    ?assertionUri a ont:Assertion .
    ?assertionUri ont:runId <#runUri> .
    ?assertionUri ?p ?o
  }
}
""".replaceAll("#runUri", RunUri(runId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }

  def saveAssertionVO(vo: AssertionVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = AssertionVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(AssertionUri(vo.id), graph)
    FutureVal(result)
  }

  def save(assertion: Assertion)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveAssertionVO(assertion.toValueObject)

  def delete(assertion: Assertion)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")
    
}

case class AssertionClosed(assertion: Assertion, contexts: Iterable[Context])


sealed trait AssertionSeverity {
  override def toString: String = this match {
    case Error => "error"
    case Warning => "warning"
    case Info => "info"
  }
}
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


