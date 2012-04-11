package org.w3.vs.model

import org.w3.vs._
import org.w3.util.URL
import scala.util.matching.Regex
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import org.scalatest.matchers.{ Matcher, MatchResult }

trait FilterMatchers {

  def accept(right: String): Matcher[Filter] = accept(URL(right))

  def accept(right: URL): Matcher[Filter] = new Matcher[Filter] {

    def apply(left: Filter) = {

      val failureMessageSuffix = left.toString + " does not accept " + right.toString

      val negatedFailureMessageSuffix = left.toString + " did accept " + right.toString

      MatchResult(
        left.passThrough(right),
        failureMessageSuffix,
        negatedFailureMessageSuffix,
        failureMessageSuffix,
        negatedFailureMessageSuffix
      )
    }
  }

}

class FilterTest extends WordSpec with MustMatchers with FilterMatchers {

  "include everything" in {

    Filter.includeEverything must accept ("http://example.com/foo")

  }

  "include prefix http://example.com/foo" in {

    val exampleFoo = Filter.includePrefix("http://example.com/foo")

    exampleFoo must accept ("http://example.com/foo")

    exampleFoo must accept ("http://example.com/foo/bar")

    exampleFoo must not (accept ("http://example.com/"))

    exampleFoo must not (accept ("http://example.com/bar"))

  }

  "include prefix http://example.com/foo and https://example.com/bar" in {

    val exampleFoo = Filter.includePrefixes("http://example.com/foo", "https://example.com/bar")

    exampleFoo must accept ("http://example.com/foo")

    exampleFoo must accept ("http://example.com/foo/bar")

    exampleFoo must accept ("https://example.com/bar")

    exampleFoo must accept ("https://example.com/bar/foo/baz")

    exampleFoo must not (accept ("http://example.com/"))

    exampleFoo must not (accept ("http://example.com/bar"))

    exampleFoo must not (accept ("https://example.com/foo"))

  }

}
