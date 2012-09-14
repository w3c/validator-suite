package org.w3.vs.assertor

import org.w3.util._
import org.w3.cssvalidator.{ CSSValidator => CSSVal }
import play.api.{ Logger, Configuration }
import java.io.File

/** An instance of the CSSValidator
 *
 */
object CSSValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val cssvalLogger = Logger("org.w3.vs.assertor.CSSValidator")

  val configuration = Configuration.load(new File("."))

  val name = "validator.css"
  
  var cssval: CSSVal = null

  lazy val port = configuration.getInt("application.css-validator.port") getOrElse 2719

  lazy val checkUri = "http://localhost:" + port + "/validator?uri="

  def start(): Unit = if (cssval == null) {
    cssvalLogger.debug("starting on port " + port)
    cssval = new CSSVal(port)
    cssval.start()
  }

  def stop(): Unit = if (cssval != null) {
    cssvalLogger.debug("stopping")
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
