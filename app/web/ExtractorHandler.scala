package org.w3.vs.web

import org.xml.sax._
import org.xml.sax.ext._
import org.xml.sax.helpers.DefaultHandler

/** http://docs.oracle.com/javase/7/docs/api/org/xml/sax/helpers/DefaultHandler.html
  * http://docs.oracle.com/javase/7/docs/api/org/xml/sax/ext/LexicalHandler.html
  */
class ExtractorHandler(baseURL: URL, extractLinks: Boolean) extends DefaultHandler with LexicalHandler {

  private var _hrefs = List[String]()

  def hrefs: List[URL] = {
    _hrefs.distinct.reverse flatMap { baseURL / _ }
  }

  var doctypeOpt: Option[Doctype] = None

  /**
   * http://download.oracle.com/javase/6/docs/api/org/xml/sax/Attributes.html
   */
  override def startElement(uri: String, name: String, qname: String, attrs: Attributes) = {
    qname match {
      case "a" if extractLinks => {
        val value = attrs.getValue("href")
        if (value != null) _hrefs ::= value
      }
      case _ => ()
    }
  }

  def comment(ch: Array[Char], start: Int, length: Int): Unit = ()
  def endCDATA(): Unit = ()
  def endDTD(): Unit = ()
  def endEntity(name: String): Unit = ()
  def startCDATA(): Unit = ()
  def startDTD(name: String, publicId: String, systemId: String): Unit = {
    doctypeOpt = Some(Doctype(name, publicId, systemId))
  }
  def startEntity(name: String): Unit = ()

}
