package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view.Helper

/** An instance of the HTMLValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org/wmvs/HEAD http://qa-dev.w3.org/wmvs/HEAD]]
 */
object HTMLValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("validator_html")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml", "application/xml", "image/svg+xml", "application/mathml+xml", "application/smil+xml")
  
  val serviceUrl = "http://validator.w3.org/check"
  
  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("ucn")))
  }

  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = Helper.encode(url)
    val query = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + query)
    validatorURL
  }
  
}
