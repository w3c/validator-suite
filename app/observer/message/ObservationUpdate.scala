package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId
import org.w3.vs.model.{Response, Assertion}
import org.w3.vs.observer.ObserverState
import play.api.libs.json._
import org.w3.vs.model._
import org.w3.util.JsInt

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait ObservationUpdate {
  def toJS: JsValue
}

/**
 * The Observation has finished, there should be no more updates after that.
 * 
 * Note: this may go away in next versions.
 */
case object Done extends ObservationUpdate {
  def toJS: JsValue = JsArray(List(JsString("OBS_FINISHED")))
}

/**
 * The user has stopped the observation
 */
case object Stopped extends ObservationUpdate {
  def toJS: JsValue = JsArray(List(JsString("STOPPED")))
}

/**
 * A coherent state for an Observation.
 * 
 * @bertails: this should not be an ObservationUpdate
 * TODO: actually, we should
 * - remove numberOfResponses and numberOfAssertions
 * - send lists of Response and ObserverState#Assertion
 */
/*case class ObservationSnapshot(
    numberOfResponses: Int,
    numberOfUrlsToBeExplored: Int,
    numberOfAssertions: Int,
    updates: Iterable[ObservationUpdate]) extends ObservationUpdate {
  def toJS: JsValue = 
    JsArray(List(
      JsString("OBS_INITIAL"),
      JsNumber(numberOfResponses),
      JsNumber(numberOfUrlsToBeExplored),
      JsNumber(numberOfAssertions)
    ))
}*/

/**
 * urls are new URLs to be explored
 */
case class NewURLsToExplore(urls: Iterable[URL]) extends ObservationUpdate {
  def toJS: JsValue = 
    JsArray(List(
      JsString("NB_EXP"),
      new JsInt(urls.size)
    ))
}

/**
 * New URLs to observe (so coming from the extraction of a link)
 */
case class NewURLsToObserve(nbUrls: Int) extends ObservationUpdate {
  def toJS: JsValue = JsArray(List(
      JsString("NB_OBS"),
      JsNumber(nbUrls)
    ))
}

/**
 * A new Response was received during the exploration
 */
case class NewResponse(response: Response) extends ObservationUpdate {
  def toJS: JsValue =
    response match {
      case HttpResponse(url, GET, httpCode, headers, extractedURLs) =>
        JsArray(List(
          JsString("GET"),
          JsNumber(httpCode),
          JsString(url.toString)
        ))
      case HttpResponse(url, HEAD, httpCode, headers, extractedURLs) =>
        JsArray(List(
          JsString("HEAD"),
          JsNumber(httpCode),
          JsString(url.toString)
        ))
      case ErrorResponse(url, errorMessage) =>
        JsArray(List(
          JsString("ERR"),
          JsString(errorMessage),
          JsString(url.toString)
        ))
    }
}

/**
 * A new Assertion was received
 */
case class NewAssertion(assertion: Assertion) extends ObservationUpdate {
  def toJS: JsValue =
    assertion match {
      case Assertion(url, assertorId, AssertionError(t)) => 
        JsArray(List(
          JsString("OBS_ERR"),
          JsString(url.toString)
        ))
      case Assertion(url, assertorId, events@Events(_)) => 
        JsArray(List(
          JsString("OBS"),
          JsString(url.toString),
          JsString(assertorId.toString),
          JsNumber(events.errorsNumber),
          JsNumber(events.warningsNumber)
        ))
    }
}
