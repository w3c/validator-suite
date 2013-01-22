package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.view.Helper
import scala.io.Source
import java.io._
import org.w3.vs.http._
import play.api.Configuration
import java.util.concurrent.{ Executors, ForkJoinPool }
import com.ning.http.client.{ AsyncHttpClientConfig, AsyncHttpClient }
import com.ning.http.client.providers.jdk._
import org.w3.util.HeadersHelper.extractCharset

object MarkupValidator extends MarkupValidator(MarkupValidatorConfiguration()) {

  val UsesHtml5Syntax = "This page uses HTML5 syntax"

  val logger = play.Logger.of(classOf[MarkupValidator])

  def fix(assertions: Iterable[Assertion]): Iterable[Assertion] = {
    assertions map {
      case assertion@Assertion(_, _, _, _, "External Checker not available", Error, _, _) =>
        assertion.copy(title = UsesHtml5Syntax)
      case assertion => assertion
    }
  }

  /** consumes the header section of an InputStream */
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

  // this HAS to stay lazy, in case we're using the distant MarkupValidator
  private lazy val client: AsyncHttpClient = {
    val Local(_, timeout, _, _) = configuration
//    val executor = new ForkJoinPool()
    val jdkProvider = new JDKAsyncHttpProviderConfig()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder
        .setAsyncHttpClientProviderConfig(jdkProvider)
        .setUserAgent("markup-val")
//        .setExecutorService(executor)
//        .setFollowRedirects(true)
        .setConnectionTimeoutInMs(timeout)
        .build()
    new AsyncHttpClient(new JDKAsyncHttpProvider(config))
  }

}

/** An instance of the MarkupValidator
  *
  */
class MarkupValidator(val configuration: MarkupValidatorConfiguration) extends FromHttpResponseAssertor with UnicornFormatAssertor {

  import OutputStreamW.pimp
  import MarkupValidator.{ fix, consumeHeaders, client }

  val id = AssertorId("markup_validator")

  val supportedMimeTypes = List("text/html", "application/xhtml+xml", "application/xml", "image/svg+xml", "application/mathml+xml", "application/smil+xml")

  def validatorURLForMachine(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    validatorURLForHuman(url, assertorConfiguration + ("output" -> List("ucn")))
  }

  override def validatorURLForHuman(url: URL, assertorConfiguration: AssertorConfiguration): URL = {
    val encoded = url.encode("UTF-8")
    val queryString = Helper.queryString(assertorConfiguration + ("uri" -> Seq(encoded)))
    val validatorURL = URL(configuration.serviceUrl + "?" + queryString)
    validatorURL
  }

  override def assert(url: URL, assertorConfig: AssertorConfiguration): Iterable[Assertion] = configuration match {
    case Local(serviceUrl, timeout, markupValBinary, markupValConf) =>
      val boundary = "------"

      // if the content is cached, then this will work just fine
      // if not, then we're accessing the web
      val response = client.prepareGet(url.toString).execute().get()
      val content = response.getResponseBodyAsStream()

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

      val charset: Option[String] = Option(response.getContentType) flatMap extractCharset

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
      // that it's the user's responsability to fork the process

      val pb = new ProcessBuilder(markupValBinary.getAbsolutePath)

      // that's how we fake the CGI environment for the script
      // TODO: check if CONTENT_LENGTH is mandatory for performance improvements
      val env = pb.environment()
      env.put("W3C_VALIDATOR_CFG", markupValConf.getAbsolutePath)
      env.put("REQUEST_METHOD", "POST")
      val queryString = Helper.queryString(assertorConfig + ("output" -> List("ucn")))
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
    case Distant(serviceUrl) =>
      val source = Source.fromURL(validatorURLForMachine(url, assertorConfig))
      assert(source)
  }
  
}
