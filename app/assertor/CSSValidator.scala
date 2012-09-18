package org.w3.vs.assertor

import org.w3.util._
import org.w3.cssvalidator.{ CSSValidator => CSSVal }
import play.api._
import java.io.File
import org.w3.vs.model._

/** An instance of the CSSValidator
 *
 */
object CSSValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val cssvalLogger = Logger("org.w3.vs.assertor.CSSValidator")

  val configuration = Configuration.load(new File("."))

  val id = AssertorId("validator_css")
  
  var cssval: CSSVal = null

  lazy val port = configuration.getInt("application.css-validator.port") getOrElse 2719

  lazy val checkUri = "http://localhost:" + port + "/validator?uri="

  def start(): Unit = if (cssval == null) {
    try {
      cssvalLogger.debug("starting on port " + port)
      cssval = new CSSVal(port)
      cssval.start()
    } catch { case be: java.net.BindException =>
      cssvalLogger.debug("already started on port " + port)
    }
  }

  def stop(): Unit = {
    if (cssval != null) {
      if (Play.maybeApplication.map(_.mode) == Some(Mode.Prod)) {
        cssvalLogger.debug("stopping")
        cssval.stop()
        cssval = null
      } else {
        cssvalLogger.debug("only stops when in Prod mode")
      }
    }
  }

  def validatorURL(encodedURL: String, assertorConfiguration: AssertorConfiguration) =
    checkUri + encodedURL + "&output=ucn&vextwarning=true&profile=css3"

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL =
    URL(validatorURL(encodedURL(url), assertorConfiguration))
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL(checkUri + encoded)
    validatorURL
  }
  
}
