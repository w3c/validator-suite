package org.w3.vs.actor.message

import org.w3.util.URL
import org.w3.vs.assertor._
import org.w3.vs.model._
import org.w3.vs.actor._
import play.api.libs.json._
import org.w3.vs.model._
import scalaz._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait RunUpdate /*{
  def toJS: JsValue
}*/

case class UpdateData(data: JobData, activity: RunActivity) extends RunUpdate {
  def toJS: JsValue = JsArray() 
    /*JsArray(List(
      JsString("JobStatus"),
      JsString(data.valueObject.jobId.toString),
      JsString(data.activity.toString),
      JsString(data.explorationMode.toString),
      JsNumber(data.resources),
      JsNumber(data.oks),
      JsNumber(data.errors),
      JsNumber(data.warnings)
    ))*/
}

/**
 * A new Response was received during the exploration
 */
case class NewResource(resource: ResourceResponse) extends RunUpdate {
  def toJS: JsValue = {
    JsArray()
    /*resource match {
      case ErrorResponse(why) => 
        JsArray(List(
          JsString("ERR"),
          JsString(why),
          JsString(resource.url.toString)
        ))
      case HttpResponse(status, headers, extractedLinks) => resource.action match {
        case GET => 
          JsArray(List(
            JsString("GET"),
            JsNumber(status),
            JsString(resource.url.toString)
          ))
        case HEAD => 
          JsArray(List(
            JsString("HEAD"),
            JsNumber(status),
            JsString(resource.url.toString)
          ))
        case _ => sys.error("TODO you should change the type :-)")
      }
    }*/
  }
}

/**
 * A new Assertion was received
 */
case class NewAssertorResponse(response: AssertorResponse) extends RunUpdate {
  
  def toJS: JsValue = JsArray() 
    /*response match {
    
    case result: AssertorResult =>
      JsArray(List(
        JsString("OBS"),
        JsString(result.url.toString),
        JsString(result.assertorId.toString),
        JsNumber(result.numberOfErrors),
        JsNumber(result.numberOfWarnings)
      ))
    case failure: AssertorFailure =>
      JsArray(List(
        JsString("OBS_ERR"),
        JsString(failure.url.toString)
      ))
  }*/
  
}

