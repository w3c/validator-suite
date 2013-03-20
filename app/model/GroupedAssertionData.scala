package org.w3.vs.model

import org.w3.util.URL

case class GroupedAssertionData(
  id: AssertionTypeId,
  assertor: AssertorId,
  lang: String,
  title: String,
  severity: AssertionSeverity,
  occurrences: Int,
  resources: Vector[URL]) {

  /** incorporates `assertion` in this GroupedAssertionData. It expects the assertion to be  */
  def +(assertion: Assertion): GroupedAssertionData = {
    this.copy(
      occurrences = this.occurrences + GroupedAssertionData.occurences(assertion),
      resources = this.resources :+ assertion.url
    )
  }

}

object GroupedAssertionData {

  def occurences(assertion: Assertion): Int =
    math.max(1, assertion.contexts.size)

  def apply(assertion: Assertion): GroupedAssertionData = {
    import assertion._
    GroupedAssertionData(
      AssertionTypeId(assertion),
      assertor,
      lang,
      title,
      severity,
      occurences(assertion),
      Vector(url))
  }

}
