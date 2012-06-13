package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.store._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation
import org.w3.banana._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

case class Context(
    id: ContextId = ContextId(),
    content: String,
    line: Option[Int], 
    column: Option[Int],
    assertionId: AssertionId)(implicit conf: VSConfiguration) {
  
  def getAssertion: FutureVal[Exception, Assertion] = Assertion.get(assertionId)

  def toValueObject: ContextVO = ContextVO(id, content, line, column, assertionId)

  def save(): FutureVal[Exception, Unit] = Context.save(this)
  
  def delete(): FutureVal[Exception, Unit] = Context.delete(this)

} 

object Context {
  
  def apply(vo: ContextVO)(implicit conf: VSConfiguration): Context = {
    import vo._
    Context(id, content, line, column, assertionId)
  }
  
  def getContextVO(id: ContextId)(implicit conf: VSConfiguration): FutureVal[Exception, ContextVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = ContextUri(id)
    FutureVal(conf.store.getNamedGraph(uri)) flatMap { graph => 
      FutureVal.pureVal[Throwable, ContextVO]{
        val pointed = PointedGraph(uri, graph)
        ContextVOBinder.fromPointedGraph(pointed)
      }(t => t)
    }
  }

  def get(id: ContextId)(implicit conf: VSConfiguration): FutureVal[Exception, Context] =
    getContextVO(id) map (Context(_))

  def fromPointedGraph(conf: VSConfiguration)(pointed: PointedGraph[conf.Rdf]): Validation[BananaException, Context] = {
    implicit val c = conf
    import conf.binders._
    for {
      vo <- ContextVOBinder.fromPointedGraph(pointed)
    } yield {
      Context(vo)
    }
  }

  def fromGraph(conf: VSConfiguration)(graph: conf.Rdf#Graph): Validation[BananaException, Iterable[Context]] = {
    import conf.diesel._
    import conf.binders._
    val assertions: Iterable[Validation[BananaException, Context]] =
      graph.getAllInstancesOf(ont.Context) map { pointed => fromPointedGraph(conf)(pointed) }
    assertions.toList.sequence[({type l[X] = Validation[BananaException, X]})#l, Context]
  }

  def getForAssertion(assertionId: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Context]] = {
    implicit val context = conf.webExecutionContext
    import conf._
    import conf.binders.{ xsd => _, _ }
    import conf.diesel._
    val query = """
CONSTRUCT {
  ?contextUri ?p ?o .
} WHERE {
  graph ?contextUri {
    ?contextUri a ont:Context .
    ?contextUri ont:assertionId <#assertionUri> .
    ?contextUri ?p ?o
  }
}
""".replaceAll("#assertionUri", AssertionUri(assertionId).toString)
    val construct = SparqlOps.ConstructQuery(query, ont)
    FutureVal(store.executeConstruct(construct)) flatMapValidation { graph => fromGraph(conf)(graph) }
  }

  def saveContextVO(vo: ContextVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = ContextVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(ContextUri(vo.id), graph)
    FutureVal(result)
  }

  def save(context: Context)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveContextVO(context.toValueObject)

  def delete(context: Context)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    sys.error("")
    
}
