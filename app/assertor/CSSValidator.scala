package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import scalaz._
import Validation._

/** An instance of the CSSValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org:8001/css-validator/ http://qa-dev.w3.org:8001/css-validator/]]
 */
object CSSValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  //val id = AssertorId("CSSValidator")
  
  def validatorURL(encodedURL: String) =
    "http://qa-dev.w3.org:8001/css-validator/validator?uri=" + encodedURL + "&output=ucn&vextwarning=true&profile=css3"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL("http://qa-dev.w3.org:8001/css-validator/validator?uri=" + encoded)
    validatorURL
  }
  
}
