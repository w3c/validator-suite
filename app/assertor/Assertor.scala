package org.w3.vs.assertor

import org.w3.vs._
import org.w3.vs.model.AssertorId

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  // TODO Fix this.
  implicit lazy val conf = org.w3.vs.Prod.configuration
  implicit lazy val executionContext = conf.assertorExecutionContext  
  
  val id: AssertorId = AssertorId() 
  val name: String
}

object Assertor {
  
  def getName(id: AssertorId): String = {
    id match {
      case CSSValidator.id => CSSValidator.name
      case HTMLValidator.id => HTMLValidator.name
      case ValidatorNu.id => ValidatorNu.name
      case I18nChecker.id => I18nChecker.name
      case _ => "Unknown assertor?"
    }
  }
  
}
