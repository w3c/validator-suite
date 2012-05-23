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
object HTMLValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  //val id = AssertorId("HTMLValidator")
  
  def validatorURL(encodedURL: String) =
    "http://qa-dev.w3.org/wmvs/HEAD/check?uri=" + encodedURL + "&charset=%28detect+automatically%29&doctype=Inline&group=0&user-agent=W3C_Validator%2F1.2&output=ucn"
  
  def validatorURLForMachine(url: URL): URL =
    URL(validatorURL(encodedURL(url)))
  
  override def validatorURLForHuman(url: URL): URL = {
    val encoded = encodedURL(url)
    val validatorURL = URL("http://qa-dev.w3.org/wmvs/HEAD/check?uri=" + encoded + "&charset=%28detect+automatically%29&doctype=Inline&group=0&user-agent=W3C_Validator%2F1.2")
    validatorURL
  }
  
}