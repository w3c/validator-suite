package org.w3.vs.view

import org.joda.time.DateTime
import org.w3.util.{FutureVal, URL}
import org.w3.vs.model.Job

case class ResourceView(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends View

object ResourceView {

  def apply(t: (URL, DateTime, Int, Int)): ResourceView =
    ResourceView(t._1, t._2, t._3, t._4)

  def fromJob(job: Job): FutureVal[Exception, Iterable[ResourceView]] = {
    job.getRun() map {
      run => run.urlArticles map ResourceView.apply _
    }
  }

  val params = Seq[String](
    "url",
    "validated",
    "warnings",
    "errors"
  )

}