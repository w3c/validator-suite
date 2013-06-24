package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.web._
import org.w3.vs.model._
import org.w3.vs.view.Helper
import play.api.Configuration
import java.io.File

/** An instance of the HTMLValidator
 *
 *  It speaks with the instance deployed at [[http://qa-dev.w3.org/wmvs/HEAD http://qa-dev.w3.org/wmvs/HEAD]]
 */
class I18nChecker(val serviceUrl: String) extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("checker_i18n")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml")

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("format" -> List("xml")))
  }
  
  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }
  
}

object I18nChecker extends I18nChecker({
  val confPath = "application.assertor.i18n-checker"
  val conf = Configuration.load(new File(".")).getConfig(confPath) getOrElse sys.error(confPath)
  val serviceUrl = conf.getString("url") getOrElse sys.error("url")
  serviceUrl
})
