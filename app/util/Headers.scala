package org.w3.util


/**
 * helper class to deal with HTTP headers
 */
class HeadersW(headers: Headers) {

  import HeadersW._

  lazy val contentTypeHeader: Option[String] = headers get "Content-Type" flatMap { _.headOption }
  lazy val mimetype: Option[String] = contentTypeHeader flatMap { extractMimeType(_) }
  lazy val charset: Option[String] = contentTypeHeader flatMap { extractCharset(_)}

}

object Headers {
  implicit val wrapHeaders = HeadersW.wrapHeaders _
  val DEFAULT_CHARSET = "UTF-8"
}

object HeadersW {

  val CONTENT_TYPE_REGEX = """^(\w+?/\w+?)(;.*)?$""".r
  val CHARSET_REGEX = """charset=(.*)$""".r

  def convertJMapJList(headers:java.util.Map[String, java.util.List[String]]):Map[String, List[String]] = {
    import scala.collection.JavaConversions._
    mapAsScalaMap(headers).toMap map { case (k, l) => (k, asScalaBuffer(l).toList) }
  }

  implicit def wrapHeaders(headers: Headers):HeadersW =
    new HeadersW(headers)

  def extractMimeType(contentTypeHeader:String):Option[String] =
    CONTENT_TYPE_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

  private def extractCharset(contentTypeHeader:String):Option[String] =
    CHARSET_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

}

