package org.apache.commons.httpclient

import org.apache.commons.httpclient.methods.GetMethod
import org.w3.vs.web.Cache
import org.w3.vs.web.URL
import org.w3.vs.model.GET
import java.io.IOException

/** that is an interesting hack: this Apache HttpClient intercepts all
  * GetMethod calls and fill it with data from the cache. If something
  * goes wrong, an IOException is raised */
class CacheOnlyHttpClient(cache: Cache) extends HttpClient {

  class CacheMissException(uri: String) extends IOException(s"cache miss ${uri}")

  override def executeMethod(method: HttpMethod): Int = try {
    val m = method.asInstanceOf[GetMethod]
    val uri = method.getURI.getURI
    cache.resource(URL(uri), GET) match {
      case Some(resource) =>
        val (status, headers, bodyStream) = resource.getCachedData().get
        m.statusLine = new StatusLine(s"HTTP/1.0 ${status} don't look here :-)")
        headers.underlying foreach { case (name, values) =>
          val value = values.mkString(",")
          m.setRequestHeader(name, value)
        }
        m.setResponseStream(bodyStream)
        status
      case None => throw new CacheMissException(uri)
    }
  } catch {
    case ioe: IOException => throw ioe
    case e: Exception => throw new IOException(e)
  }


}
