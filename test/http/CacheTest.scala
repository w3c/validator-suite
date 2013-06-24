package org.w3.vs.http

import org.scalatest._
import org.scalatest.matchers.MustMatchers
import java.nio.file.Files.createTempDirectory
import org.w3.vs.util.URL
import org.w3.vs.util.Util._
import org.w3.vs.model._
import java.io._
import scalax.io._
import scalax.io.JavaConverters._
import scala.collection.JavaConverters._

class CacheTest extends WordSpec with MustMatchers {

  val directory = {
    val d = createTempDirectory("cache-test-").toFile
    d.deleteOnExit()
    d
  }

  val cache = Cache(directory)

  "retrieving a resource that was never cached should be a miss" in {

    cache.resource(URL("http://example.com/never-cached"), GET) must be(None)
    
  }

  "retrieving a previously cached resource must be a hit" in {
    
    List[HttpMethod](GET, HEAD) foreach { method =>

      val url = URL("http://example.com/ok/" + method)
  
      val hr = HttpResponse(url, method, 200, Headers(Map("foo" -> List("bar", "baz"))), List.empty, None)
  
      val content = "foo"
      val bais = new ByteArrayInputStream(content.getBytes("UTF-8"))
      val bodyContent = Resource.fromInputStream(bais)
  
      cache.save(hr, bodyContent) must be('success)
  
      val r = cache.resource(url, method).flatMap(_.get().toOption)
  
      r must be(Some(hr))

    }

  }

  "a Cache must be able to cache errors" in {
    
    List[HttpMethod](GET, HEAD) foreach { method =>

      val url = URL("http://example.com/error/" + method)
  
      val er = ErrorResponse(url, method, "server not reachable")
  
      cache.save(er) must be('success)
  
      val r = cache.resource(url, method).flatMap(_.get().toOption)
  
      r must be(Some(er))

    }

  }

  "a cached resource must be retrievable from ResponseCache#get" in {

    List[HttpMethod](GET, HEAD) foreach { method =>

      val url = URL("http://example.com/rc/" + method)
  
      val headers =
        Headers(Map("foo" -> List("bar", "baz"), "User-Agent" -> List("w3c/validator-suite")))

      val status = 201

      val hr = HttpResponse(url, method, status, headers, List.empty, None)
  
      val content = "foo"
      val bais = new ByteArrayInputStream(content.getBytes("UTF-8"))
      val bodyContent = Resource.fromInputStream(bais)
  
      cache.save(hr, bodyContent) must be('success)
  
      val cacheResponse = cache.get(url.toURI, method.toString, Map("foo" -> List("bar").asJava).asJava)

      cacheResponse must not be(null)

      Resource.fromInputStream(cacheResponse.getBody()).string must be(content)

      val rHeaders = cacheResponse.getHeaders.asScala.mapValues(_.asScala.toList).toMap

      (rHeaders - null) must be(headers.underlying)

      (rHeaders(null).head contains status.toString) must be (true)

    }

  }

}
