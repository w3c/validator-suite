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
    occurrences: Int,
    contexts: Iterable[ContextView]) extends View {

  //val occurrences: Int = contexts.size

}

object AssertionView {
  def fromAssertions(assertions: Iterable[Assertion]): Iterable[AssertionView] = {
    assertions map (apply _)
  }

  import org.w3.util.FutureVal
  import org.w3.util.FutureVal._

  def fromJob(job: Job): FutureVal[Exception, Iterable[AssertionView]] = {
    job.getAssertions().map{_.map(apply(_))}
  }

  def apply(assertion: Assertion): AssertionView = {
    AssertionView(
      assertion.url,
      Assertor.getKey(assertion.assertorId),
      assertion.severity,
      Html(assertion.title),
      assertion.description.map(Html.apply _),
      assertion.timestamp,
      scala.math.max(1, assertion.contexts.size),
      assertion.contexts.toSeq.sorted(
        Ordering[(Int, Int)].on[Context](context => (context.line.getOrElse(1000000), context.column.getOrElse(1000000)))
      ).map(ContextView.apply _)
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

