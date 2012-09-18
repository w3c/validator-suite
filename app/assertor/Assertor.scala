package org.w3.vs.assertor

import org.w3.vs.model._

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  // // TODO Fix this.
  // implicit lazy val conf = org.w3.vs.Prod.configuration
  // implicit lazy val executionContext = conf.assertorExecutionContext  

  def id: AssertorId

  def name: String = id.id

}

object Assertor {

  val names = Iterable(
    CSSValidator.name,
    HTMLValidator.name,
    ValidatorNu.name,
    I18nChecker.name
  )

  val all: Iterable[Assertor] = Iterable(
    CSSValidator,
    HTMLValidator,
    ValidatorNu,
    I18nChecker
  )

  val get: PartialFunction[String, FromHttpResponseAssertor] = Map[String, FromHttpResponseAssertor](
    ValidatorNu.name -> ValidatorNu,
    HTMLValidator.name -> HTMLValidator,
    I18nChecker.name -> I18nChecker,
    CSSValidator.name -> CSSValidator)

}
