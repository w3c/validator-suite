package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import play.api.libs.json._
import play.api.templates.HtmlFormat


/**
 *  Validator.nu
 * 
 *  http://wiki.whatwg.org/wiki/Validator.nu_Web_Service_Interface
 */
object ValidatorNu extends FromHttpResponseAssertor {

  val name = "validator.nu"
  
  def validatorURL(encodedURL: String) =
    "http://validator.w3.org/nu/?doc=" + encodedURL + "&out=json"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL("http://validator.w3.org/nu/?doc=" + encoded)
    validatorURL
  }

  /*def assert(url: URL)(implicit context: ExecutionContext): FutureVal[Throwable, Iterable[Assertion]] = FutureVal {
    val urlCon = validatorURLForMachine(url).openConnection()
    urlCon.setConnectTimeout(2000)
    //urlCon.setReadTimeout(10000)
    val content = Source.fromInputStream(urlCon.getInputStream).getLines.mkString("\n")*/
  def assert(source: Source): Iterable[Assertion] = {
    val json = Json.parse(source.getLines.mkString("\n"))
    val url = URL((json \ "url").asInstanceOf[JsString].value)
    val messages = json \ "messages"
    messages.asInstanceOf[JsArray].value.map { obj =>
      val assertionId = AssertionId()
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
      Assertion(url, name, contexts, "en", title, severity, None)
    }
  }
  
}
