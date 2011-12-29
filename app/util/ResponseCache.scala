package org.w3.util

import java.net._
import java.io._

case class CachedResponse(in: InputStream) extends CacheResponse {
  def getBody = in
  def getHeaders = null
}

object CachedResponse {
  def fromFile(file: File): CachedResponse = new CachedResponse(new FileInputStream(file))
  def fromString(filename: String): CachedResponse =
    CachedResponse.fromFile(new File(filename))
}

object CatalogLike extends ResponseCache {

  val xhtml1 = "^http://www.w3.org/TR/xhtml1/DTD/(.+)$".r
  val html4 = "^http://www.w3.org/TR/html4/(.+)$".r

  def get(uri:URI, rqstMethod:String, rqstHeaders:java.util.Map[java.lang.String,java.util.List[java.lang.String]]) = {
    // println("## " + uri)
    // TODO use a map
    // TODO take the content from the classpath
    uri.toString match {
      case xhtml1(file) => CachedResponse.fromString("src/main/resources/dtds/xhtml1/" + file)
      case html4(file) => CachedResponse.fromString("src/main/resources/dtds/html4/" + file)
      case _ => null
    }
  }
  def put(uri:URI, conn:URLConnection) = null
}
