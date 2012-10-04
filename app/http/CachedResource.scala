package org.w3.vs.http

import org.w3.vs.model._
import java.io.{ File, BufferedInputStream, FileInputStream, InputStream }
import scalax.io._
import scalax.io.JavaConverters._
import java.net._
import java.util.{ LinkedHashMap, ArrayList, List => jList, Map => jMap }
import scala.collection.JavaConverters._
import org.w3.util.{ URL, Headers }
import Cache._

case class CachedResource(cache: Cache, url: URL) {

  val filename = math.abs(url.toString.hashCode).toString

  def metaFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString)
  def responseHeadersFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".respHeaders")
  def errorFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".error")
  def bodyFile(method: HttpMethod) = new File(cache.directory, filename + "." + method.toString + ".error")
  
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

  def save(rr: ResourceResponse): Unit = rr match {
    case ErrorResponse(url, method, why) => {
      metaFile(method).asBinaryWriteChars(Codec.UTF8).write("ERROR " + System.currentTimeMillis() + " " + url)
      errorFile(method).asBinaryWriteChars(Codec.UTF8).write(why)
    }
    case HttpResponse(url, method, status, headers, body) => {
      metaFile(method).asBinaryWriteChars(Codec.UTF8).write("OK " + System.currentTimeMillis() + " " + url)
      bodyFile(method).asBinaryWriteChars(Codec.UTF8).writeCharsProcessor.acquireAndGet { wc =>
        wc.write("null: " + status + "\n")
        headers foreach { case (header, values) =>
          wc.write(header + ": " + values.mkString(",") + "\n")
        }
      }
    }
  }

  def get(method: HttpMethod): Option[ResourceResponse] =
    try {
      if (isError(method)) {
        val errorMessage = errorFile(method).asBinaryReadChars(Codec.UTF8).slurpString
        Some(ErrorResponse(url, method, errorMessage))
      } else {
        val (status, headers) = getStatusHeaders(method)
        val body = method match {
          case HEAD => ""
          case GET => bodyFile(method).asBinaryReadChars(Codec.UTF8).slurpString
        }
        Some(HttpResponse(url, method, status, headers, body))
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
