package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import play.api.libs.json._
import play.api.templates.{Html, HtmlFormat}
import org.w3.vs.view.Helper
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsNumber
import org.w3.vs.model.Context


/**
 *  Validator.nu
 * 
 *  http://wiki.whatwg.org/wiki/Validator.nu_Web_Service_Interface
 */
class ValidatorNu(serviceUrl: String) extends FromHttpResponseAssertor {

  val id = AssertorId("validator_nu")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml", "application/xml", "image/svg+xml", "application/mathml+xml", "application/smil+xml")

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("out" -> List("json")))
  }
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("doc" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }

  def assert(source: Source): Iterable[Assertion] = {
    val json = Json.parse(source.getLines.mkString("\n"))
    val url = URL((json \ "url").asInstanceOf[JsString].value)
    val messages = json \ "messages"
    messages.asInstanceOf[JsArray].value.map { obj =>
      val severity = AssertionSeverity((obj \ "type") match {
        case JsString("non-document-error") => throw new Exception("validator.nu failed")
        case JsString("info") => (obj \ "subType") match {
          case JsString("warning") => "warning"
          case _ => "info"
        }
        case JsString(s) => s
        case _ => throw new Exception("malformed json") // TODO
      })
      val title = (obj \ "message") match {case JsString(s) => HtmlFormat.escape(s).text; case _ => throw new Exception("malformed json")}
      val lastLine = (obj \ "lastLine") match {case JsNumber(bigDec) => Some(bigDec.toInt); case _ => None}
      val lastCol = (obj \ "lastColumn") match {case JsNumber(bigDec) => Some(bigDec.toInt); case _ => None}
      val extract = (obj \ "extract") match {case JsString(s) => Some(HtmlFormat.escape(s).text); case _ => None}
      val contexts = extract match {
        case Some(code) => List(Context(code.trim, lastLine, lastCol)) // The model needs to accept a range of lines/column
        case _ => List()
      }
      Assertion(url, id, contexts, "en", title, severity, None)
    }
  }
  
}
