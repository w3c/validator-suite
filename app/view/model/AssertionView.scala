package org.w3.vs.view.model

import play.api.templates.Html
import org.w3.vs.model._
import org.w3.vs.view._

trait AssertionView extends View {
    def assertor: String
    def severity: AssertionSeverity
    def title: Html
    def description: Option[Html]
    def occurrences: Int
    def isEmpty: Boolean
}
