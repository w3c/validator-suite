package org.w3.vs.web

import java.util.{ Map => jMap, List => jList }
import scala.collection.JavaConverters._

object Headers {

  val DEFAULT_CHARSET = "UTF-8"

  val CONTENT_TYPE_REGEX = """^(\w*/\w*(\+\w+)?)(;.*)?$""".r

  val CHARSET_REGEX = """charset=(.*)$""".r

  def extractMimeType(contentTypeHeader: String): Option[String] =
    CONTENT_TYPE_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

  def extractCharset(contentTypeHeader: String): Option[String] =
    CHARSET_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

  def apply(headers: jMap[String, jList[String]]): Headers =
    new Headers(headers.asScala.mapValues(_.asScala.toList).toMap)

  def apply(headers: Map[String, List[String]]): Headers =
    new Headers(headers)

  val empty: Headers = new Headers(Map.empty)

}

// should be collection.immutable.ListMap to preserver order insertion
class Headers(val underlying: Map[String, List[String]]) extends AnyVal {

  def contentTypeHeader: Option[String] = underlying.get("Content-Type").flatMap { _.headOption }
  
  def mimetype: Option[String] = contentTypeHeader flatMap Headers.extractMimeType
  
  def charset: Option[String] = contentTypeHeader flatMap Headers.extractCharset

  def location: Option[String] = underlying.get("Location") orElse (underlying.get("location")) flatMap { _.headOption }

  def locationURL(root: URL): Option[URL] = location.flatMap(root / _)

  def asJava: jMap[String, jList[String]] = underlying.mapValues(_.asJava).asJava

}
