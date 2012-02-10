package org.w3.util.localwebsite

import java.net._
import java.io._
import java.util.{Map => jMap, List => jList, HashMap => jHashMap, LinkedList}
import scala.collection.JavaConverters._

class LocalCache(base: File, mode: CacheMode) extends ResponseCache {
  
  import LocalCache._
  
  def cachedResponseFor(uri: URI, rqstMethod: String): CacheResponse = {
    val (bodyFilename, headerFilename) = getFiles(uri)
    
    val headerFile = new File(base, headerFilename)
    val headers =
      if (headerFile.exists) {
        val lines = io.Source.fromFile(headerFile).getLines
        val map: Map[String, jList[String]] = lines.map {
          case regex("null", statusline) => (null, List(statusline).asJava)
          case regex(key, values) => (key, values.split(",").toList.asJava)
        }.toMap
        map.asJava
      } else {
        null
      }
    
    val body =
      if (rqstMethod == "GET") {
        val bodyFile = new File(base, bodyFilename)
        new FileInputStream(bodyFile)
      } else {
        new ByteArrayInputStream(Array[Byte]())
      }
    
    new CacheResponse {
      def getBody = body
      def getHeaders = headers
    }
  }
  
  def get(uri: URI, rqstMethod: String, rqstHeaders: jMap[String, jList[String]]): CacheResponse = {
    if (mode == AccessFileCache) {
      try {
        cachedResponseFor(uri, rqstMethod)
      } catch {
        case e => println("$$$ "+e.getMessage()); throw e
      }
    } else {
      null
    }
  }

  def put(uri: URI, conn: URLConnection): CacheRequest =
    if (mode == Store) {
      val headers = conn.getHeaderFields().asScala map { case (k, v) => "%s: %s" format (k, v.asScala.mkString(",")) } mkString "\n"
      val (bodyFilename, headerFilename) = getFiles(uri)
      val headerFile = new BufferedWriter(new FileWriter(new File(base, headerFilename)))
      headerFile.write(headers)
      headerFile.close()
      val cacheRequest = new CacheRequest {
        // looks like this is not invoked for a HEAD
        def getBody: OutputStream = {
          val bodyFile = new File(base, bodyFilename)
          new FileOutputStream(bodyFile)
        }
        def abort = ()
      }
      cacheRequest
    } else {
      null
    }
  
}

object LocalCache {
  
  def getFiles(uri: URI): (String, String) = {
    val filename = math.abs(uri.hashCode).toString
    (filename+".body", filename+".headers")
  }
  
  val regex = """^([^:]+):\s*(.*)$""".r

  def notValidator(uri: URI): Boolean = !(uri.getAuthority == "qa-dev.w3.org")

  def usingLocalCache[T](base: String, mode: CacheMode = AccessFileCache)(body: => T): T = {
    lazy val t = body
    try {
      val localCache = new LocalCache(new File(base), mode)
      ResponseCache.setDefault(localCache)
      t
    } catch {
      case e => println(e.getStackTraceString)
    } finally {
      ResponseCache.setDefault(null)
    }
    t
  }
  
}
