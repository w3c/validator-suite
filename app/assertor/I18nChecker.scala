package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import scalaz._
import Validation._

/** An instance of the HTMLValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org/wmvs/HEAD http://qa-dev.w3.org/wmvs/HEAD]]
 */
object I18nChecker extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val name = "I18n-Checker"
  
  def validatorURL(encodedURL: String) =
    "http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encodedURL + "&format=xml"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL("http://qa-dev.w3.org/i18n-checker-test/check?uri=" + encoded)
    validatorURL
  }
  
}