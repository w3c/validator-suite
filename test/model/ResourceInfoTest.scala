package org.w3.vs.model

import org.w3.vs.web.URL
import org.w3.vs.web.Headers
import org.scalatest._
import org.scalatest.Inside._
import org.scalatest.matchers._

class ResourceInfoTest extends WordSpec with Matchers {

  "a redirect with a relative URL should return the absolute URL" in {

    val rr = HttpResponse(
      URL("http://example.com/foo/"),
      GET,
      302,
      Headers(Map("Location" -> List("/bar/baz"))),
      List.empty,
      None)

    val ri = ResourceInfo(rr)

    ri should be(Redirect(302, URL("http://example.com/bar/baz")))

  }

  "a redirect with an absolute URL should return the same absolute URL" in {

    val rr = HttpResponse(
      URL("http://example.com/foo/"),
      GET,
      302,
      Headers(Map("Location" -> List("http://other.example.com/bar/baz"))),
      List.empty,
      None)

    val ri = ResourceInfo(rr)

    ri should be(Redirect(302, URL("http://other.example.com/bar/baz")))

  }

  "a redirect with no Location header should be an error" in {

    val rr = HttpResponse(
      URL("http://example.com/foo/"),
      GET,
      302,
      Headers.empty,
      List.empty,
      None)

    val ri = ResourceInfo(rr)

    inside (ri) { case InfoError(_) => () }

  }

  "a redirect with an invalid Location header should be an error" in {

    val rr = HttpResponse(
      URL("http://example.com/foo/"),
      GET,
      302,
      Headers(Map("Location" -> List("torrent://other.example.com/bar/baz"))),
      List.empty,
      None)

    val ri = ResourceInfo(rr)

    inside (ri) { case InfoError(_) => () }

  }

}
