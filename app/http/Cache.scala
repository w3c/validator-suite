package org.w3.vs.http

import org.w3.vs.model._
import java.io.File
import java.net._
import java.util.{ List => jList, Map => jMap }
import org.w3.util.URL
import scala.util.Try
import java.io.InputStream
import scalax.io._
import scalax.io.JavaConverters._

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
  def resource(url: URL, method: HttpMethod): Option[CachedResource] =
    CachedResource(this, url, method).toOption

  def get(uri: URI, rqstMethod: String, rqstHeaders: jMap[String, jList[String]]): CacheResponse = {
    val cacheResponseOpt = for {
      method <- HttpMethod.fromString(rqstMethod)
      url = URL(uri.toURL)
      cachedResource <- resource(url, method)
      cacheResponse <- cachedResource.asCacheResponse().toOption
    } yield {
      logger.debug(rqstMethod + " " + uri)
      cacheResponse
    }
    cacheResponseOpt getOrElse null
  }

  def put(uri: URI, conn: URLConnection): CacheRequest = null

  def save(er: ErrorResponse): Try[Unit] = Try {
    val cr = new CachedResource(this, er.url, er.method)
    cr.remove()
    cr.metaFile.asBinaryWriteChars(Codec.UTF8).write("ERROR " + System.currentTimeMillis() + " " + er.url)
    cr.errorFile.asBinaryWriteChars(Codec.UTF8).write(er.why)
  }

  def save(hr: HttpResponse, bodyContent: InputResource[InputStream]): Try[Unit] = Try {
    val cr = new CachedResource(this, hr.url, hr.method)
    cr.remove()
    cr.metaFile.asBinaryWriteChars(Codec.UTF8).write("OK " + System.currentTimeMillis() + " " + hr.url)
    cr.responseHeadersFile.asBinaryWriteChars(Codec.UTF8).writeCharsProcessor.foreach { owc =>
      val wc = owc.asWriteChars
      wc.write("null: " + hr.status + "\n")
      hr.headers foreach { case (header, values) =>
        wc.write(header + ": " + values.mkString(",") + "\n")
      }
    }
    bodyContent.copyDataTo(cr.bodyFile.asOutput)
  }
  

}
