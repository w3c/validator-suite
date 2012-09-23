package org.w3.vs.view

import org.w3.vs.model._
import play.api.libs.json._
import scalaz.Scalaz._
import org.joda.time._
import org.w3.vs.view.model.JobView

case object JobsUpdate {

  def json(job: JobView) = {
    JsArray(List(
      JsString("Job"),
      JsString(job.id.toString),
      JsString(job.name),
      JsString(job.entrypoint.toString),
      JsString(job.status.toString),
      JsString(job.completedOn.fold(_.toString(), "")),
      JsString(job.completedOn.fold(Helper.formatTime _, "Never")),
      JsString(job.completedOn.fold(Helper.formatLegendTime _, "")),
      JsNumber(job.warnings),
      JsNumber(job.errors),
      JsNumber(job.resources),
      JsNumber(job.maxResources),
      JsNumber(job.health)
    ))
  }

  def json(jobId: JobId, data: JobData, activity: RunActivity): JsValue = {
    JsArray(List(
      JsString("Job"),
      JsString(jobId.toString),
      JsNull,
      JsNull,
      JsString(activity.toString),
      JsNull,
      JsNull,
      JsNull,
      JsNumber(data.warnings),
      JsNumber(data.errors),
      JsNumber(data.resources),
      JsNull,
      JsNumber(data.health)
    ))
  }

  def json(jobId: JobId, completedOn: DateTime): JsValue = {
    JsArray(List(
      JsString("Job"),
      JsString(jobId.toString),
      JsNull,
      JsNull,
      JsNull,
      JsString(completedOn.toString),
      JsString(Helper.formatTime(completedOn)),
      JsString(Helper.formatLegendTime(completedOn)),
      JsNull,
      JsNull,
      JsNull,
      JsNull,
      JsNull
    ))
  }
}

//case object ResourceUpdate {
//
//  def json(resource: ResourceResponse): JsValue = {
//    resource match {
//      case resource: HttpResponse => {
//        JsArray(List(
//          JsString("Resource"),
//          JsString(resource.id.toString),
//          JsString(resource.url.toString)
//        ))
//      }
//      case resource: ErrorResponse => {
//        JsArray(List(
//          JsString("FetchError"),
//          JsString(resource.id.toString)
//        ))
//      }
//    }
//  }
//}

case object AssertorUpdate {

  def json(result: AssertorResult, timestamp: DateTime): JsValue = {
    JsArray(List(
      JsString("AssertorResult"),
      JsString(result.sourceUrl.toString),
      JsString(Helper.formatTime(timestamp)),
      JsNumber(result.warnings),
      JsNumber(result.errors)
    ))
  }

}

