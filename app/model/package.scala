package org.w3.vs

import org.w3.vs.model._

package object model {

  type AssertorConfiguration = Map[AssertorId, Map[String, List[String]]]

  object AssertorConfiguration {
    import org.w3.vs.assertor._
    val default: AssertorConfiguration =
      Map(
        CSSValidator.id -> Map.empty,
        HTMLValidator.id -> Map.empty,
        I18nChecker.id -> Map.empty)
  }

}
