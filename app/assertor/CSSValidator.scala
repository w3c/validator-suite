package org.w3.vs.assertor

import org.w3.util._
import org.w3.cssvalidator.{ CSSValidator => CSSVal }

/** An instance of the CSSValidator
 *
 */
object CSSValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val name = "validator.css"
  
  var cssval: CSSVal = null

  val port = 9001
  val checkUri = "http://localhost:" + port + "/validator?uri="

  def start(): Unit = if (cssval == null) {
    cssval = new CSSVal(port)
    cssval.start()
  }

  def stop(): Unit = if (cssval != null) {
    cssval.stop()
  }

  def validatorURL(encodedURL: String) =
    checkUri + encodedURL + "&output=ucn&vextwarning=true&profile=css3"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL(checkUri + encoded)
    validatorURL
  }
  
}
