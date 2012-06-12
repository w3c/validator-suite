package org.w3.vs.view

import org.w3.util._
import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.assertor._
import play.api.libs.json._
import scalaz._
import org.joda.time.{ DateTime, DateTimeZone }

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
      // TODO add lastCompleted
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
    //implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
    val assertMessages: Iterable[JsValue] = assertions.groupBy(_.url).map{
      case (url, assertions) => 
        JsArray(List(
          JsString(url.toString),
          // TODO: I don't maintain a <time> element with javascript.
          // Ask someone if that semantic is still relevant post js
          JsString(Helper.formatTime(assertions.map(_.timestamp).max)),
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

