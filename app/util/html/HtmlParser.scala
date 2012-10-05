package org.w3.util.html

import org.w3.util.URL
import java.io._
import org.xml.sax._
import scalax.io._

object HtmlParser extends HtmlParser

/**
 * http://about.validator.nu/htmlparser/apidocs/nu/validator/htmlparser/sax/HtmlParser.html
 */
trait HtmlParser {
  
  def parse(
      baseURL: URL,
      resource: InputResource[InputStream],
      encodingOpt: Option[String] = None): List[URL] = {
    resource.acquireAndGet { inputStream =>
      val encoding = encodingOpt getOrElse "UTF8"
      val inputSource = {
        val is = new InputSource(inputStream)
        is.setEncoding(encoding)
        is
      }
      val extractor = new ExtractorHandler(baseURL)
      val parser = {
        import nu.validator.htmlparser.common.XmlViolationPolicy
        val p = new nu.validator.htmlparser.sax.HtmlParser(XmlViolationPolicy.ALLOW)
        p.setContentHandler(extractor)
        p.setErrorHandler(extractor)
        p
      }
      parser.parse(inputSource)
      val hrefs = extractor.hrefs
      hrefs
    }
  }
  
}

