package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view.Helper
import scala.io.Source
import java.io._
import org.w3.vs.http._
import play.api.Configuration


object MarkupValidator extends MarkupValidator {

  val UsesHtml5Syntax = "This page uses HTML5 syntax"

  val logger = play.Logger.of(classOf[MarkupValidator])

  def fix(assertions: Iterable[Assertion]): Iterable[Assertion] = {
    assertions map {
      case assertion@Assertion(_, _, _, _, "External Checker not available", Error, _, _) =>
        assertion.copy(title = UsesHtml5Syntax)
      case assertion => assertion
    }
  }

  def consumeHeaders(is: InputStream): Unit = {
    var continue = true
    var newline = true
    var counter = 0
    while(continue && counter <= 300) {
      counter += 1
      val c = is.read().toChar
      if (c == '\n') {
        if (newline) {
          continue = false
        } else {
          newline = true
        }
      } else {
        newline = false
      }
    }
  }

}

/** An instance of the MarkupValidator
  *
  */
class MarkupValidator extends FromHttpResponseAssertor with UnicornFormatAssertor {

  import OutputStreamW.pimp
  import MarkupValidator.{ fix, logger, consumeHeaders }

  val id = AssertorId("markup_validator")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml", "application/xml", "image/svg+xml", "application/mathml+xml", "application/smil+xml")

  lazy val configuration = Configuration.load(new File("."))
  lazy val serviceUrl: String = configuration.getString("application.local-validator.markup-validator.url") getOrElse sys.error("application.local-validator.markup-validator.url")
  lazy val enable: Boolean = {
    val enable: Boolean = configuration.getBoolean("application.local-validator.markup-validator.check.enable") getOrElse false
    def checkBinaryExists: Boolean = new File(checkBinary).isFile
    def checkConfigExists: Boolean = new File(checkConfig).isFile
    lazy val ok: Boolean = checkBinaryExists && checkConfigExists
    if (enable && (!ok))
      logger.warn(s"Issue with the configuration for a local Markup Validator, falling back to ${serviceUrl}")
    enable && ok
  }
  lazy val checkBinary: String = configuration.getString("application.local-validator.markup-validator.check.binary").get
  lazy val checkConfig: String = configuration.getString("application.local-validator.markup-validator.check.conf").get
  
  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("ucn")))
  }

  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(serviceUrl + "?" + queryString)
    validatorURL
  }

  override def assert(url: URL, configuration: AssertorConfiguration): Iterable[Assertion] = {
    if (enable) {
      val boundary = "------"

      // if the content is cached, then this will work just fine
      // if not, then we're accessing the web
      val urlConnection = url.underlying.openConnection()
      val headers = urlConnection.getHeaderFields()
      val content = urlConnection.getInputStream()

      // multiform data, read the result here after flush
      val data = new ByteArrayOutputStream()
      // filters the bytes, read the number at the end
      val cos = new CountingOutputStream(data)
      // write here for better performance
      val buffer = new BufferedOutputStream(cos)

      // uploaded_file

      buffer.writeCRLN(boundary)
      // looks like the markup-validator uses blindly $filename as the URL that was checked
      buffer.writeCRLN(s"""Content-Disposition: form-data; name="uploaded_file"; filename="${url}"""")
      buffer.writeCRLN("Content-Type: text/html")
      buffer.writeCRLN("")
      org.apache.commons.io.IOUtils.copy(content, buffer)
      buffer.writeCRLN("")

      // other POST parameters

      val charset: Option[String] = Option(urlConnection.getHeaderField("Content-Type")) flatMap org.w3.util.HeadersHelper.extractCharset

      val params = Map(
        "charset" -> (charset getOrElse "(detect automatically)"),
        "doctype" -> "Inline",
        "group" -> "0",
        "user-agent" -> "W3C_Validator/1.3",
        "output" -> "ucn")

      params foreach { case (k, v) =>
          buffer.writeCRLN(boundary)
          buffer.writeCRLN(s"""Content-Disposition: form-data; name="${k}"""")
          buffer.writeCRLN("")
          buffer.writeCRLN(v)
      }

      // ends the multidata form

      buffer.writeCRLN(boundary + "--")
      buffer.flush()

      // starts the process
      // this is blocking, but it's fine as we considerer
      //that it's the user's responsability to fork the process

      val pb = new ProcessBuilder(checkBinary)

      // that's how we fake the CGI environment for the script
      // TODO: check if CONTENT_LENGTH is mandatory for performance improvements
      val env = pb.environment()
      env.put("W3C_VALIDATOR_CFG", checkConfig)
      env.put("REQUEST_METHOD", "POST")
      val queryString = Helper.queryString(configuration + ("output" -> List("ucn")))
      env.put("QUERY_STRING", queryString)
      env.put("HTTP_HOST", "valid.w3.org")
      env.put("CONTENT_TYPE", "multipart/form-data; boundary=---")
      env.put("CONTENT_LENGTH", cos.counter.toString) // size in bytes
//      env.put("HTTP_ACCEPT_ENCODING", "UTF-8")
      val p = pb.start()

      // sends the content of the POST to stdin

      val stdin = new BufferedOutputStream(p.getOutputStream())
      data.writeTo(stdin)
      stdin.flush()
      stdin.close()

      // gives the output to the parser

      val stdout = new BufferedInputStream(p.getInputStream())
      // we need to ignore the HTTP headers before the XML parser gets to the datastream
      consumeHeaders(stdout)
      val source = Source.fromInputStream(stdout)
      p.waitFor()
      val assertions = assert(source)
      fix(assertions)
    } else {
      val source = Source.fromURL(validatorURLForMachine(url, configuration))
      assert(source)
    }
  }
  
}
