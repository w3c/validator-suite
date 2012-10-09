package org.w3.vs.http

import org.w3.vs.model._
import java.io._
import scalax.io._
import scalax.io.JavaConverters._
import java.net._
import java.util.{ LinkedHashMap, ArrayList, List => jList, Map => jMap }
import scala.collection.JavaConverters._
import org.w3.util.{ URL, Headers }
import Cache._

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
case class CachedResource(cache: Cache, url: URL) {

  val filename = math.abs(url.toString.hashCode).toString

  def metaFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString)
  def responseHeadersFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".respHeaders")
  def errorFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".error")
  def bodyFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".body")

  /**
   * remove the informations for this url/method from the cache
   */
  def remove(method: HttpMethod): Unit = {
    import java.nio.file.Files.{ deleteIfExists => delete }
    delete(metaFile(method).toPath)
    delete(errorFile(method).toPath)
    delete(responseHeadersFile(method).toPath)
    delete(bodyFile(method).toPath)
  }

  /**
   * get the state for this url/method
   */  
  def getCachedResourceState(method: HttpMethod): CachedResourceState = {
    val line = metaFile(method).asBinaryReadChars(Codec.UTF8).lines().head
    val metaRegex(responseStateS, timestamp, reqUrl) = line
    assert(reqUrl == url.toString)
    val responseState = responseStateS match {
      case "OK" => OK
      case "ERROR" => ERROR
    }
    responseState
  }

  def isError(method: HttpMethod): Boolean = errorFile(method).exists

  /**
   * get the status+headers
   */  
  def getStatusHeaders(method: HttpMethod): (Int, Headers) = {
    val lines = responseHeadersFile(method).asBinaryReadChars(Codec.UTF8).lines()
    val status = {
      val headerRegex("null", status) = lines.head
      status.toInt
    }
    val builder = Map.newBuilder[String, List[String]]
    lines.tail foreach { case headerRegex(key, values) =>
      builder += (key -> values.split(",").toList)
    }
    val headers = builder.result()
    (status, headers)
  }

  def save(er: ErrorResponse): Unit = {
    remove(er.method)
    metaFile(er.method).asBinaryWriteChars(Codec.UTF8).write("ERROR " + System.currentTimeMillis() + " " + er.url)
    errorFile(er.method).asBinaryWriteChars(Codec.UTF8).write(er.why)
  }

  def save(hr: HttpResponse, bodyContent: InputResource[InputStream]): Unit = {
    remove(hr.method)
    metaFile(hr.method).asBinaryWriteChars(Codec.UTF8).write("OK " + System.currentTimeMillis() + " " + hr.url)
    responseHeadersFile(hr.method).asBinaryWriteChars(Codec.UTF8).writeCharsProcessor.foreach { owc =>
      val wc = owc.asWriteChars
      wc.write("null: " + hr.status + "\n")
      hr.headers foreach { case (header, values) =>
        wc.write(header + ": " + values.mkString(",") + "\n")
      }
    }
    bodyContent.copyDataTo(bodyFile(hr.method).asOutput)
  }

  def get(method: HttpMethod): Option[ResourceResponse] =
    try {
      if (isError(method)) {
        val errorMessage = errorFile(method).asBinaryReadChars(Codec.UTF8).slurpString
        Some(ErrorResponse(url, method, errorMessage))
      } else {
        val (status, headers) = getStatusHeaders(method)
        val bodyContent: InputResource[InputStream] = method match {
          case HEAD => Resource.fromInputStream(new ByteArrayInputStream(Array.empty[Byte]))
          case GET => Resource.fromInputStream(new FileInputStream(bodyFile(method)))
        }
        Some(HttpResponse(url, method, status, headers, bodyContent))
      }
    } catch { case e =>
      cache.logger.error(method.toString + " " + url.toString + ": " + e.getMessage)
      None
    }

  def asCacheResponse(method: HttpMethod): Option[CacheResponse] = {
    if (isError(method))
      None
    else {
      val cr = new CacheResponse {

        def getBody(): InputStream = new BufferedInputStream(new FileInputStream(bodyFile(method)))
        
        def getHeaders(): jMap[String, jList[String]] = {
          val (status, headers) = getStatusHeaders(method)
          val map = new LinkedHashMap[String, jList[String]](headers.size + 1)
          val statusSingletonList = {
            val l = new ArrayList[String](1)
            l.add(status.toString)
            l
          }
          map.put(null, statusSingletonList)
          headers foreach { case (header, values) =>
            map.put(header, values.asJava)
          }
          map
        }

      }
      Some(cr)
    }

  }

}
