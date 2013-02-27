package org.w3.vs.model

import org.joda.time.{DateTimeZone, DateTime}
import play.api.templates.Html
import org.w3.util.URL

case class GroupedAssertionData(
  assertor: AssertorId,
  lang: String,
  title: String,
  severity: AssertionSeverity,
  occurrences: Int,
  resources: List[URL] = List.empty)
