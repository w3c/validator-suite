package org.w3.vs.view

import org.w3.vs.model._
import play.api.libs.json._
import scalaz.Scalaz._
import org.joda.time._

case object JobsUpdate {
  
  def json(data: JobData, jobId: JobId, activity: RunActivity): JsValue = {
    JsArray(List(
      JsString("Dashboard"),
      JsString(jobId.toString),
      JsString(activity.toString),
      JsNumber(data.resources),
      JsNumber(data.errors),
      JsNumber(data.warnings),
      JsNumber(data.health),
      JsString(data.completedAt.fold(Helper.formatTime _, "Never"))
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

