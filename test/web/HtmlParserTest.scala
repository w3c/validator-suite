package org.w3.vs.web

import java.io._
import org.xml.sax._
import scalax.io._

import org.scalatest._
import org.scalatest.Matchers
import org.w3.vs.model._
import org.w3.vs.view.Helper

class HtmlParserTest extends WordSpec with Matchers {

  "parsing http://www.w3.org/TR/2012/CR-html5-20121217/ and extracting links and doctype informations" in {
    val url = URL("http://www.w3.org/TR/2012/CR-html5-20121217/")
    val resource = scalax.io.Resource.fromInputStream(new FileInputStream("test/resources/CR-html5-20121217.html"))
    val (urls, doctypeOpt) = HtmlParser.parse(url, resource, extractLinks = true)
    urls should contain(URL("http://www.w3.org/"))
    doctypeOpt.get should be('html5)
  }

}
