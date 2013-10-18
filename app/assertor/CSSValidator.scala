package org.w3.vs.assertor

import org.w3.vs.web.URL
import org.w3.vs.model._
import org.w3.vs.view.Helper

class CSSValidator(val serviceUrl: String) extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("validator_css")

  val supportedMimeTypes = List("text/css", "text/html", "application/xhtml+xml", "image/svg+xml")

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("ucn")) + ("vextwarning" -> List("true")))
  }
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }

}
