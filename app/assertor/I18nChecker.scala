package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

/** An instance of the HTMLValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org/wmvs/HEAD http://qa-dev.w3.org/wmvs/HEAD]]
 */
class I18nChecker(val serviceUrl: String) extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("checker_i18n")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml")

  def validatorURL(encodedURL: String, assertorConfiguration: AssertorConfiguration) =
    "http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encodedURL + "&format=xml"
  
  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL =
    URL(validatorURL(url.encode("UTF-8"), assertorConfiguration))
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val validatorURL = URL("http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encoded)
    validatorURL
  }
  
}

object I18nChecker extends I18nChecker({
  val serviceUrl = "http://qa-dev.w3.org/i18n-checker-test/check"
  serviceUrl
})
