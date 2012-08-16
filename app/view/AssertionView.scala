package org.w3.vs.view

import org.joda.time.DateTime
import play.api.templates.Html
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.assertor.Assertor

case class AssertionView(
    url: URL,
    assertorName: String,
    severity: AssertionSeverity,
    message: Html,
    description: Option[Html],
    validated: DateTime,
    contexts: Iterable[ContextView]) extends View {

  val occurrences: Int = contexts.size

}

object AssertionView {

  import org.w3.util.FutureVal
  import org.w3.util.FutureVal._

  def fromJob(job: Job): FutureVal[Exception, Iterable[AssertionView]] = {
    job.getRun() map {
      run => run.assertions.toIterable.map(apply(_))
    }
  }

  def apply(assertion: Assertion): AssertionView = {
    AssertionView(
      assertion.url,
      Assertor.getName(assertion.assertorId),
      assertion.severity,
      Html(assertion.title),
      assertion.description.map(Html.apply _),
      assertion.timestamp,
      assertion.contexts.map(ContextView.apply _)
    )
  }

  val params = Seq[String](
    "assertor",
    "severity",
    "occurrences",
    "message",
    "description",
    "validated",
    "context"
  )

}

