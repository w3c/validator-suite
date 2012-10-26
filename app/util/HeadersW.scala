package org.w3.util

object HeadersHelper {

  val CONTENT_TYPE_REGEX = """^(\w+?/\w+?)(;.*)?$""".r

  val CHARSET_REGEX = """charset=(.*)$""".r

  def extractMimeType(contentTypeHeader: String): Option[String] =
    CONTENT_TYPE_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

  def extractCharset(contentTypeHeader: String): Option[String] =
    CHARSET_REGEX findFirstMatchIn contentTypeHeader map { _.group(1) }

}

