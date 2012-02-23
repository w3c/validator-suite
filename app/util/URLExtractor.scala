package org.w3.util

object URLExtractor {
  
  final def fromHtml(url: URL, body: String): List[URL] = {
    val encoding = "UTF-8"
    val reader = new java.io.StringReader(body)
    html.HtmlParser.parse(url, reader, encoding) map URL.clearHash
  }
  
  final def fromCSS(url: URL, body: String): List[URL] = {
    List.empty
  }
  
}