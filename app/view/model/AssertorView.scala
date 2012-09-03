package org.w3.vs.view.model

import org.w3.vs.model._
import org.w3.vs.assertor.Assertor
import org.w3.vs.view._

case class AssertorView(
    name: String,
    errors: Int,
    warnings: Int) extends View

object AssertorView {

  def fromAssertions(assertions: Iterable[Assertion]): Iterable[AssertorView] = {
    assertions.groupBy(_.assertor).map {
       case (assertor, assertions) => {
         val errors = assertions.foldLeft(0) {
           case (count, assertion) =>
             count + (assertion.severity match {
               case Error => scala.math.max(assertion.contexts.size, 1)
               case _ => 0
             })
         }
         val warnings = assertions.foldLeft(0) {
           case (count, assertion) =>
             count + (assertion.severity match {
               case Warning => scala.math.max(assertion.contexts.size, 1)
               case _ => 0
             })
         }
         AssertorView(
           assertor,
           errors,
           warnings
         )
       }
     }
  }

}