package org.w3.vs.web

import org.w3.vs.model._
import java.io.File
import java.net.{ URI, CacheRequest, ResponseCache, CacheResponse, URLConnection }
import java.util.{ List => jList, Map => jMap }
import org.w3.vs.util.file
import org.w3.vs.util.implicits._
import scala.util.Try
import java.io.InputStream
import scalax.io._
import scalax.io.JavaConverters._
import play.api.Configuration

sealed trait CachedResourceState
case object OK extends CachedResourceState
case object ERROR extends CachedResourceState

object Cache {

  val logger = play.Logger.of(classOf[Cache])

  val metaRegex = """^(OK|ERROR) (\d+) (.*)$""".r

  val headerRegex = """^([^:]+):\s*(.*)$""".r

  /** retrieve and set up the cache (side-effects!) */
  def apply(configuration: Configuration): Option[Cache] = {
    val httpCacheConf = configuration.getConfig("application.http-cache") getOrElse sys.error("application.http-cache")
    if (httpCacheConf.getBoolean("enable") getOrElse sys.error("enable")) {
      val directory = new File(httpCacheConf.getString("directory") getOrElse sys.error("directory"))
      if (httpCacheConf.getBoolean("create-if-not-exist") getOrElse sys.error("create-if-not-exist")) {
        if (! directory.exists && ! directory.mkdir())
          sys.error("could not create HTTP Cache directory " + directory.getAbsolutePath)
      }
      if (httpCacheConf.getBoolean("clean-at-startup") getOrElse sys.error("clean-at-startup")) {
        if (directory.exists) file.delete(directory)
        if (! directory.mkdir()) sys.error("could not create HTTP Cache directory " + directory.getAbsolutePath)
      }
      assert(directory.exists, "directory [" + directory.getAbsolutePath + "] for the HTTP Cache must exist")
      val cache = Cache(directory)
      Some(cache)
    } else {
      None
    }

  }

}

case class Cache(directory: File) extends ResponseCache {

  import Cache.logger

  assert(directory.isDirectory)

  def reset(): Unit = {
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
    val userAgent: String = {
      val uas = rqstHeaders.get("User-Agent")
      if (uas == null || uas.isEmpty) "unknown-user-agent" else uas.get(0)
    }
    val url = URL(uri.toURL)
    val cacheResponseOpt = for {
      method <- HttpMethod.fromString(rqstMethod)
      cachedResource <- resource(url, method)
      cacheResponse <- cachedResource.asCacheResponse().toOption
    } yield {
//      logger.debug(rqstMethod + " " + uri)
      cacheResponse
    }
    //println(s"[${userAgent}] ${rqstMethod} ${uri}")
    val hitmiss = if (cacheResponseOpt.isDefined) "hit" else "miss"
    if (userAgent.startsWith("markup-val"))
      logger.debug(s"markup-val: cache $hitmiss for $uri")
    else if (userAgent.startsWith("Jigsaw"))
      logger.debug(s"css-val: cache $hitmiss for $uri")
    else if (userAgent.startsWith("markup-val"))
      logger.debug(s"validator.nu: cache $hitmiss for $uri")
    cacheResponseOpt getOrElse null
  }

  def put(uri: URI, conn: URLConnection): CacheRequest = null

  def retrieveAndCache(url: URL, method: HttpMethod): Unit = {

    import com.ning.http.client._

    try {
      val client = new AsyncHttpClient()
      val response: Response = client.prepareGet(url.toString).setHeader("Accept-Language", "en-us,en;q=0.5").execute().get()
      val (hr, bodyContent) = response.asHttpResponse(url, method)
      this.save(hr, bodyContent)
    } catch { case e: Exception =>
      val er = ErrorResponse(url, method, e.getMessage)
      this.save(er)
    }

  }

  def save(er: ErrorResponse): Try[Unit] = Try {
    import scalax.file.{ FileOps, Path }
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
      wc.write(s"null: HTTP/1.0 ${hr.status} FIXED STATUS TEXT\n")
      hr.headers.underlying foreach { case (header, values) =>
        wc.write(header + ": " + values.mkString(",") + "\n")
      }
    }
    bodyContent.copyDataTo(cr.bodyFile.asOutput)
  }

  private var previousCache: ResponseCache = null

  def setAsDefaultCache(): Unit = ResponseCache.setDefault(this)

  def restorePreviousCache(): Unit = ResponseCache.setDefault(previousCache)

}
