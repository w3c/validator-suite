package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation
import org.w3.banana._

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

  def getForAssertion(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Context]] = {
    // TODO
    implicit val context = conf.webExecutionContext
    FutureVal.successful(Iterable())
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
