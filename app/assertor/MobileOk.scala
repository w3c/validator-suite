package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.web._
import org.w3.vs.model._
import org.w3.vs.view.Helper

object MobileOk extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("checker_mobile")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml")

  val serviceUrl = "http://validator.w3.org/mobile/check"

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("unicorn")))
  }

  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("docAddr" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }

}
