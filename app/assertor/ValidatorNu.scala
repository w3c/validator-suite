package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import scalaz._
import Validation._
import play.api.libs.json._
import play.api.templates.HtmlFormat
import Json._


/** 
 *  Validator.nu
 * 
 *  http://wiki.whatwg.org/wiki/Validator.nu_Web_Service_Interface
 */
object ValidatorNu extends FromURLAssertor {

  val id = AssertorId("ValidatorNu")
  
  def validatorURL(encodedURL: String) =
    "http://validator.w3.org/nu/?doc=" + encodedURL + "&out=json"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL("http://validator.w3.org/nu/?doc=" + encoded)
    validatorURL
  }

  def assert(url: URL): Validation[Throwable, Iterable[RawAssertion]] = fromTryCatch {	
	val urlCon = validatorURLForMachine(url).openConnection()
	urlCon.setConnectTimeout(2000)
	//urlCon.setReadTimeout(10000)
	val content = Source.fromInputStream(urlCon.getInputStream).getLines.mkString("\n")
    val json = Json.parse(content)
    val responseUrl = URL((json \ "url").asInstanceOf[JsString].value)
    val messages = json \ "messages"
    messages.asInstanceOf[JsArray].value.map {obj =>
      // really doesn't look like a good habit to use these sys.error calls but you learn by example, you know
	  val typ = (obj \ "type") match {case JsString(s) => s; case _ => sys.error("malformed json")}
      val message = (obj \ "message") match {case JsString(s) => s; case _ => sys.error("malformed json")}
	  val lastLine = (obj \ "lastLine") match {case JsNumber(bigDec) => Some(bigDec.toInt); case _ => None}
	  val lastCol = (obj \ "lastColumn") match {case JsNumber(bigDec) => Some(bigDec.toInt); case _ => None}
	  val extract = (obj \ "extract") match {case JsString(s) => Some(HtmlFormat.escape(s).text); case _ => None}
	  val context = extract match {
	    case Some(code) => Seq(Context(code, url.toString, lastLine, lastCol)) // The model needs to accept a range of lines/column
	    case _ => Seq()
	  }
	  RawAssertion(typ, "no freakin id", "en", context, message, None)
	}
  }
  
}