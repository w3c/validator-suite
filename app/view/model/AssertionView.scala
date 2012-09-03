package org.w3.vs.view.model

import play.api.templates.Html
import org.w3.vs.model._
import org.w3.vs.view._

trait AssertionView extends View {
    val assertorName: String
    val severity: AssertionSeverity
    val message: Html
    val description: Option[Html]
    val occurrences: Int
}
