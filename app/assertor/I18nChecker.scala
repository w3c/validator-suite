package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view.Helper

/** An instance of the HTMLValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org/wmvs/HEAD http://qa-dev.w3.org/wmvs/HEAD]]
 */
object I18nChecker extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("checker_i18n")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml")

  val serviceUrl = "http://qa-dev.w3.org/i18n-checker-test/check"
  
  def validatorURL(encodedURL: String, assertorConfiguration: AssertorConfiguration) =
    "http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encodedURL + "&format=xml"
  
  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL =
    URL(validatorURL(Helper.encode(url), assertorConfiguration))
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = Helper.encode(url)
    val validatorURL = URL("http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encoded)
    validatorURL
  }
  
}
