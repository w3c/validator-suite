package org.w3.vs.assertor

import org.w3.vs.model._
import LocalValidators.{ ValidatorNu, CSSValidator }

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {

  def id: AssertorId

  def name: String = id.id

}

object Assertor {

  val logger = play.Logger.of(classOf[Assertor])

  val names = Iterable(
    CSSValidator.name,
    MarkupValidator.name,
    ValidatorNu.name,
    I18nChecker.name,
    MobileOk.name
  )

  val all: Iterable[Assertor] = Iterable(
    CSSValidator,
    MarkupValidator,
    ValidatorNu,
    I18nChecker,
    MobileOk
  )

  val get: PartialFunction[String, FromHttpResponseAssertor] = Map[String, FromHttpResponseAssertor](
    ValidatorNu.name -> ValidatorNu,
    MarkupValidator.name -> MarkupValidator,
    I18nChecker.name -> I18nChecker,
    CSSValidator.name -> CSSValidator,
    MobileOk.name -> MobileOk)

  val getById: PartialFunction[AssertorId, FromHttpResponseAssertor] = Map[AssertorId, FromHttpResponseAssertor](
    ValidatorNu.id -> ValidatorNu,
    MarkupValidator.id -> MarkupValidator,
    I18nChecker.id -> I18nChecker,
    CSSValidator.id -> CSSValidator,
    MobileOk.id -> MobileOk)


}
