package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.joda.time._
import org.w3.vs.assertor._
import scalaz.Validation

case class Context(
    id: ContextId = ContextId(),
    content: String,
    line: Option[Int], 
    column: Option[Int],
    assertionId: AssertionId)(implicit conf: VSConfiguration) {
  
  def getAssertion: FutureVal[Exception, Assertion] = Assertion.get(assertionId)

  def toValueObject: ContextVO = ContextVO(id, content, line, column, assertionId)

  def save(): FutureVal[Exception, Unit] = Context.save(this)

} 

object Context {
  
  def apply(
      content: String, 
      line: Option[Int], 
      column: Option[Int], 
      assertionId: AssertionId)(implicit conf: VSConfiguration): Context =
    Context(ContextId(), content, line, column, assertionId)

  def apply(vo: ContextVO)(implicit conf: VSConfiguration): Context = {
    import vo._
    Context(id, content, line, column, assertionId)
  }
  
  def get(id: ContextId)(implicit conf: VSConfiguration): FutureVal[Exception, Context] = sys.error("ni")

  def getForAssertion(id: AssertionId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Context]] = sys.error("ni")

  def save(context: Context)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(())
  }

}
