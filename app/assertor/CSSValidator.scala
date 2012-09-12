package org.w3.vs.assertor

import org.w3.util._
import org.w3.cssvalidator.{ CSSValidator => CSSVal }
import com.typesafe.config.ConfigFactory

/** An instance of the CSSValidator
 *
 */
object CSSValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  /* doc at http://typesafehub.github.com/config/latest/api/ */
  val configuration = ConfigFactory.load()

  val name = "validator.css"
  
  var cssval: CSSVal = null

  val port = configuration.getInt("application.css-validator.port")
  val checkUri = "http://localhost:" + port + "/validator?uri="

  def start(): Unit = if (cssval == null) {
    cssval = new CSSVal(port)
    cssval.start()
  }

  def stop(): Unit = if (cssval != null) {
    cssval.stop()
    cssval = null
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
