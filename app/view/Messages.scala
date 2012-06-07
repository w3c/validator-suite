package org.w3.vs.view

import org.w3.vs._
import org.w3.vs.model._
import play.api.libs.json._
import scalaz._

case object JobsUpdate {
  
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

case object ResourceUpdate {
  
  def json(resource: ResourceResponse): JsValue = {
    resource match {
      case resource: HttpResponse => {
        JsArray(List(
          JsString("Resource"),
          JsString(resource.id.toString),
          JsString(resource.url.toString)
          //JsString(resource.timestamp.toString)
        ))
      }
      case resource: ErrorResponse => {
        JsArray(List(
          JsString("FetchError"),
          JsString(resource.id.toString)
          //JsString(resource.url.toString),
          //JsString(resource.timestamp.toString)
        ))
      }
    }
  }
}

case object AssertorUpdate {
  
  def json(assertions: Iterable[Assertion]): JsValue = {
    val assertMessages: Iterable[JsValue] = assertions.groupBy(_.url).map{
      case (url, assertions) => 
        JsArray(List(
          JsString(url.toString),
          JsString(assertions.head.timestamp.toString),
          JsNumber(assertions.count(_.severity == Warning)),
          JsNumber(assertions.count(_.severity == Error))
        ))
    }
    JsArray(List(
      JsString("Assertions"),
      JsArray(assertMessages.toSeq)
    ))
  }
}

