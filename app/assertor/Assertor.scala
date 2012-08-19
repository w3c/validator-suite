package org.w3.vs.assertor

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
  val key: String
}

object Assertor {

  val keys = Iterable(
    CSSValidator.key,
    HTMLValidator.key,
    ValidatorNu.key,
    I18nChecker.key
  )

  def getKey(id: AssertorId): String = {
    id match {
      case CSSValidator.id => CSSValidator.key
      case HTMLValidator.id => HTMLValidator.key
      case ValidatorNu.id => ValidatorNu.key
      case I18nChecker.id => I18nChecker.key
      case _ => "unknown assertor with id " + id.toString
    }
  }

  val get: PartialFunction[String, FromHttpResponseAssertor] = Map[String, FromHttpResponseAssertor](
    ValidatorNu.key -> ValidatorNu,
    HTMLValidator.key -> HTMLValidator,
    I18nChecker.key -> I18nChecker,
    CSSValidator.key -> CSSValidator)

}
