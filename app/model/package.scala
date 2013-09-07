package org.w3.vs

package object model {

  type AssertorsConfiguration = Map[AssertorId, AssertorConfiguration]

  type AssertorConfiguration = Map[String, List[String]]

  object AssertorsConfiguration {
    import org.w3.vs.assertor._
    import LocalValidators.{ ValidatorNu, CSSValidator }
    val default: AssertorsConfiguration =
      Map(
        CSSValidator.id -> Map.empty,
        ValidatorNu.id -> Map.empty,
        I18nChecker.id -> Map.empty)
  }

}
