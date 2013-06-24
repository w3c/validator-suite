package org.w3.vs.model

import org.w3.vs.web.URL

case class GroupedAssertionData(
  id: AssertionTypeId,
  assertor: AssertorId,
  lang: String,
  title: String,
  severity: AssertionSeverity,
  occurrences: Int,
  resources: Map[URL, Int]) { // resources should be a Set. Ideally Map{Int, URL], (occurrences -> url)

  /** incorporates `assertion` in this GroupedAssertionData. It expects
    * the assertion to share the same AssertionTypeId than this
    * GroupedAssertionData
    */
  def +(assertion: Assertion): GroupedAssertionData = {
    val newResources = resources.get(assertion.url) match {
      case None => resources + (assertion.url -> assertion.occurrences)
      // because contexts are grouped (on one given url) and because groupedAssertionData are grouped by AssertorTypedId we should never get here
      // (url + (assertorId + title))
      case Some(counter) => resources + (assertion.url -> (counter + assertion.occurrences))
    }
    this.copy(
      occurrences = this.occurrences + assertion.occurrences,
      resources = newResources
    )
  }

}

object GroupedAssertionData {

  def apply(assertion: Assertion): GroupedAssertionData = {
    import assertion._
    GroupedAssertionData(
      AssertionTypeId(assertion),
      assertor,
      lang,
      title,
      severity,
      assertion.occurrences,
      Map(url -> assertion.occurrences)
    )
  }

}
