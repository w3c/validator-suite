package org.w3.vs.web

import java.io._
import org.xml.sax._
import scalax.io._

object HtmlParser extends HtmlParser

/**
 * http://about.validator.nu/htmlparser/apidocs/nu/validator/htmlparser/sax/HtmlParser.html
 */
trait HtmlParser {
  
  /** extract the urls in the given document, as well as the Doctype informations if present
    * this is raw extraction:
    * - the order of the urls is not specified
    * - they are not necesseraly absolute
    * - there can be duplicates
    */
  def parse(
      baseURL: URL,
      resource: InputResource[InputStream],
      encodingOpt: Option[String] = None,
      extractLinks: Boolean): (List[URL], Option[Doctype]) = {
    resource.acquireAndGet { inputStream =>
      val encoding = encodingOpt getOrElse "UTF8"
      val inputSource = {
        val is = new InputSource(inputStream)
        is.setEncoding(encoding)
        is
      }
      val extractor = new ExtractorHandler(baseURL, extractLinks)
      val parser = {
        import nu.validator.htmlparser.common.XmlViolationPolicy
        val p = new nu.validator.htmlparser.sax.HtmlParser(XmlViolationPolicy.ALLOW)
        p.setContentHandler(extractor)
        p.setErrorHandler(extractor)
        p.setReportingDoctype(true)
        p.setLexicalHandler(extractor)
        p
      }
      parser.parse(inputSource)
      (extractor.hrefs, extractor.doctypeOpt)
    }
  }
  
}

