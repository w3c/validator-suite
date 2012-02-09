package org.w3.util.localwebsite

import java.net._
import java.io._
import java.util.{Map => jMap, List => jList}

class LocalCache(base: File) extends ResponseCache {

  type LocalFile = String
  type MimeType = String
  
  /**
   * loads mimetypes from an httrack log file
   */
  val mimeTypes: Map[URI, (LocalFile, MimeType)] = {
    assert(base.exists, "LocalCache: %s must exist" format base.getAbsolutePath)
    val logs = new File(base, "hts-cache/new.txt")
    assert(logs.exists, "httrack's log file %s must exist" format logs.getAbsolutePath)
    val (headLine, lines) = {
      val all = io.Source.fromFile(logs).getLines().filterNot{_.isEmpty}.toSeq
      (all.head, all.tail)
    }
    val headers: Map[String, Int] = headLine.split("\\t").zip(0 to 20).toMap
    val urlIndex = headers("URL")
    val localfileIndex = headers("localfile")
    val mimetypeIndex = headers("MIME")
    lines.map { line: String =>
      val values: Array[String] = line.split("\\t")
      val url: URI = new URI(values(urlIndex))
      val localfile: LocalFile = values(localfileIndex)
      val mimetype: MimeType = values(mimetypeIndex)
      val c = url -> (localfile, mimetype)
      c
    }.filterNot{ case (url, (localfile, mimetype)) => localfile.isEmpty }.toMap
  }
  
  
  def error(msg: String) = {
    println(msg)
    sys.error(msg)
  }
  
  def singleton(s: String): java.util.List[String] = {
    val l = new java.util.LinkedList[String]
    l.add(s)
    l
  }
  
  def cachedResponseFor(uri: URI): CacheResponse = {
    println("*** "+uri)
    val (localfile, mimetype) = mimeTypes.get(uri) getOrElse error("couldn't find uri %s" format uri.toString)
    println(localfile + " ; " + mimetype)
    val file = new File(base, localfile)
    if (file.exists)
      new CacheResponse {
        def getBody = new FileInputStream(file)
        def getHeaders = {
          val headers = new java.util.HashMap[String, java.util.List[String]]
          headers.put(null, singleton("200"))
          headers.put("Content-Type", singleton(mimetype + "; charset=utf-8"))
          headers
        }
      }
    else
      error("%s => couldn't find %s on disk" format (uri.toString, file.getAbsolutePath))
  }

  def get(uri: URI, rqstMethod: String, rqstHeaders: jMap[String, jList[String]]): CacheResponse = {
    println("### " + uri)
    try {
     cachedResponseFor(uri)
    } catch {
      case e => null
    }
  }

  def put(uri: URI, conn: URLConnection) = {
    import scala.collection.JavaConverters._
    println("&&&"+conn.getHeaderFields().asScala.map{case (k, v) => (k, v.asScala)})
    
    
    null
  }

}

object LocalCache {
  
  def usingLocalCache[T](base: String)(body: => T): T = {
    lazy val t = body
    try {
      val localCache = new LocalCache(new File(base))
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
