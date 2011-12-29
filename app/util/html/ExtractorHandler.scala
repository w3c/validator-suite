package org.w3.util.html

import org.w3.util.URL
import java.io._
import org.xml.sax._
import org.xml.sax.helpers.DefaultHandler

/**
 * http://download.oracle.com/javase/6/docs/api/org/xml/sax/helpers/DefaultHandler.html
 */
class ExtractorHandler(baseURL: URL) extends DefaultHandler {

  private var _hrefs = List[String]()

  def hrefs: List[URL] = _hrefs.reverse flatMap { baseURL / _ }

  /**
   * http://download.oracle.com/javase/6/docs/api/org/xml/sax/Attributes.html
   */
  override def startElement (uri: String, name: String, qname: String, attrs: Attributes) = {
    qname match {
      case "a" => Option(attrs.getValue("href")) foreach { value => _hrefs ::= value }
      case _ => ()
    }
  }
}
