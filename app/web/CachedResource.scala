package org.w3.vs.web

import org.w3.vs.model._
import java.io._
import scalax.io._
import scalax.io.JavaConverters._
import java.net._
import java.util.{ LinkedHashMap, ArrayList, List => jList, Map => jMap }
import scala.collection.JavaConverters._
import org.w3.vs.web._
import Cache._
import scala.util.Try

object CachedResource {

  def apply(cache: Cache, url: URL, method: HttpMethod): Try[CachedResource] = Try {
    val cr = new CachedResource(cache, url, method)
    assert(cr.metaFile.exists, "the ." + method + " file must exist")
    cr
  }

}

/**
 * a cached resource from the cache
 *
 * the url by itself is not enough to get what's cached,
 * so you'll need to provide the method that was used when caching
 *
 * this implementation also caches the errors (basically just the error message)
 * - a 404 is not an error because the server could be reached
 * - a failed connection, or a DNS issue, are the typical errors
 */
class CachedResource private[web] (cache: Cache, url: URL, method: HttpMethod) {

  val filename = math.abs(url.toString.hashCode).toString

  val metaFile = new File(cache.directory, filename + "." + method.toString)

  val responseHeadersFile = new File(cache.directory, filename + "." + method.toString + ".respHeaders")
  val errorFile = new File(cache.directory, filename + "." + method.toString + ".error")
  val bodyFile = new File(cache.directory, filename + "." + method.toString + ".body")

  /**
   * remove the informations for this url/method from the cache
   */
  def remove(): Unit = {
    import java.nio.file.Files.{ deleteIfExists => delete }
    delete(metaFile.toPath)
    delete(errorFile.toPath)
    delete(responseHeadersFile.toPath)
    delete(bodyFile.toPath)
  }

  /**
   * get the state for this url/method
   */  
  def getCachedResourceState(): CachedResourceState = {
    val line = metaFile.asBinaryReadChars(Codec.UTF8).lines().head
    val metaRegex(responseStateS, timestamp, reqUrl) = line
    assert(reqUrl == url.toString)
    val responseState = responseStateS match {
      case "OK" => OK
      case "ERROR" => ERROR
    }
    responseState
  }

  def isError: Boolean = errorFile.exists

  /**
   * get the status+headers
   */  
  def getStatusHeaders(): (Int, Headers) = {
    val lines = responseHeadersFile.asBinaryReadChars(Codec.UTF8).lines()
    val status = {
      val headerRegex("null", status) = lines.head
      status.split(" ")(1).toInt
    }
    val builder = Map.newBuilder[String, List[String]]
    lines.tail foreach { case headerRegex(key, values) =>
      builder += (key -> values.split(",").toList)
    }
    val headers = Headers(builder.result())
    (status, headers)
  }

  def getCachedData(): Try[(Int, Headers, InputStream)] = Try {
    if (isError) {
      val errorMessage = errorFile.asBinaryReadChars(Codec.UTF8).string
      throw new IOException(errorMessage)
    } else {
      val (status, headers) = getStatusHeaders()
      val bodyContent: InputStream = method match {
        case HEAD => new ByteArrayInputStream(Array.empty[Byte])
        case GET => new BufferedInputStream(new FileInputStream(bodyFile))
      }
      (status, headers, bodyContent)
    }
  }


  def get(): Try[ResourceResponse] = Try {
    if (isError) {
      val errorMessage = errorFile.asBinaryReadChars(Codec.UTF8).string
      ErrorResponse(url, method, errorMessage)
    } else {
      val (status, headers) = getStatusHeaders()
      val bodyContent: InputResource[InputStream] = method match {
        case HEAD => Resource.fromInputStream(new ByteArrayInputStream(Array.empty[Byte]))
        case GET => Resource.fromInputStream(new FileInputStream(bodyFile))
      }
      HttpResponse(url, method, status, headers, bodyContent)
    }
  }

  def asCacheResponse(): Try[CacheResponse] = Try {
    val headers: jMap[String, jList[String]] = {
      val (status, headers) = getStatusHeaders()
      val map = new LinkedHashMap[String, jList[String]](headers.underlying.size + 1)
      val statusSingletonList = {
        val l = new ArrayList[String](1)
        l.add("HTTP/1.0 " + status.toString + " FIXED STATUS TEXT")
        l
      }
      map.put(null, statusSingletonList)
      headers.underlying foreach { case (header, values) =>
        map.put(header, values.asJava)
      }
      map
    }

    val cr = new CacheResponse {

      def getBody(): InputStream = new BufferedInputStream(new FileInputStream(bodyFile))
      
      def getHeaders(): jMap[String, jList[String]] = headers

    }
    
    cr
  }

}
