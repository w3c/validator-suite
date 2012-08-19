package org.w3.vs.model


case class AssertorSelector(
  name: String,
  // maps a mime-type to a list of assertor name
  map: Map[String, List[String]])

object AssertorSelector {

  implicit def assertorSelector2Map(as: AssertorSelector): Map[String, List[String]] = as.map

  val noAssertor: AssertorSelector = AssertorSelector("no-assertor", Map.empty)

  val simple: AssertorSelector = {
    import org.w3.vs.assertor._
    AssertorSelector(
      "simple-assertor-selector",
      Map(
        "text/html" -> List(ValidatorNu.key, /*HTMLValidator.name,*/ I18nChecker.key),
        "application/xhtml+xml" -> List(ValidatorNu.key, /*HTMLValidator.name,*/ I18nChecker.key),
        "text/css" -> List(CSSValidator.key)))
  }

}

