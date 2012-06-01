package org.w3.vs.view

import org.w3.vs._
import org.w3.vs.model._
import play.api.libs.json._
import scalaz._

case object DashboardUpdate {
  
  def json(data: JobData, activity: RunActivity): JsValue = {
    JsArray(List(
      JsString("Dashboard"),
      JsString(data.jobId.toString),
      JsString(activity.toString),
      JsNumber(data.resources),
      JsNumber(data.errors),
      JsNumber(data.warnings),
      JsNumber(data.health)
    ))
  }
  
}