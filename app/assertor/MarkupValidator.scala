package org.w3.vs.assertor

import org.w3.vs.util._
import org.w3.vs.model._
import org.w3.vs.view.Helper
import scala.io.Source
import java.io._
import org.w3.vs.web._
import play.api.Configuration
import java.util.concurrent.{ Executors, ForkJoinPool }
import com.ning.http.client.{ AsyncHttpClientConfig, AsyncHttpClient }
import com.ning.http.client.providers.jdk._

/** An instance of the MarkupValidator
  */
class MarkupValidator(val serviceUrl: String) extends FromHttpResponseAssertor with UnicornFormatAssertor {

  val id = AssertorId("markup_validator")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml", "application/xml", "image/svg+xml", "application/mathml+xml", "application/smil+xml")

  val UsesHtml5Syntax = "This page uses HTML5 syntax"

  def fix(assertions: Iterable[Assertion]): Iterable[Assertion] = {
    assertions map {
      case assertion@Assertion(_, _, _, _, _, "External Checker not available", Error, _, _) =>
        assertion.copy(title = UsesHtml5Syntax)
      case assertion => assertion
    }
  }

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("ucn")))
  }

  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }

  override def assert(url: URL, configuration: AssertorConfiguration): Iterable[Assertion] =
    fix(super.assert(url, configuration))

}

object MarkupValidator extends MarkupValidator({
  val confPath = "application.assertor.markup-validator"
  val conf = Configuration.load(new File(".")).getConfig(confPath) getOrElse sys.error(confPath)
  val serviceUrl = conf.getString("url") getOrElse sys.error("url")
  serviceUrl
})
