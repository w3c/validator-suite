package org.w3.vs.http

import org.w3.vs.model._
import java.io.File
import java.net._
import java.util.{ List => jList, Map => jMap }
import org.w3.util.URL

sealed trait CachedResourceState
case object OK extends CachedResourceState
case object ERROR extends CachedResourceState

object Cache {

  val metaRegex = """^(OK|ERROR) (\d+) (.*)$""".r

  val headerRegex = """^([^:]+):\s*(.*)$""".r
  
}

case class Cache(directory: File) extends ResponseCache {

  assert(directory.isDirectory)

  val logger = play.Logger.of(classOf[Cache])

  def reset: Unit = {
    directory.listFiles foreach { file =>
      val r = file.delete()
      if (!r) sys.error("couldn't delete " + file.getAbsolutePath)
    }
  }

  /**
   * typically, you want to write something like
   *   val resourceResponse = cache.resource(url).flatMap(_.get(method))
   */
  def resource(url: URL): Option[CachedResource] =
    try {
      Some(CachedResource(this, url))
    } catch {
      case e => None
    }

  def get(uri: URI, rqstMethod: String, rqstHeaders: jMap[String, jList[String]]): CacheResponse = {
    val cacheResponseOpt = for {
      method <- HttpMethod.fromString(rqstMethod)
      url = URL(uri.toURL)
      cachedResource <- resource(url)
      cacheResponse <- cachedResource.asCacheResponse(method)
    } yield {
      logger.debug(rqstMethod + " " + uri)
      cacheResponse
    }
    cacheResponseOpt getOrElse null
  }

  def put(uri: URI, conn: URLConnection): CacheRequest = null

}
