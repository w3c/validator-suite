package org.w3.vs.assertor

object Assertors {

  val get: PartialFunction[String, FromHttpResponseAssertor] = Map[String, FromHttpResponseAssertor](
    ValidatorNu.name -> ValidatorNu,
    HTMLValidator.name -> HTMLValidator,
    I18nChecker.name -> I18nChecker,
    CSSValidator.name -> CSSValidator)

}
