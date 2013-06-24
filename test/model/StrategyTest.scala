package org.w3.vs.model

import org.w3.vs.util.URL
import org.w3.vs.web.Headers
import org.scalatest.{ Filter => ScalaTestFilter, _ }
import org.scalatest.matchers._
import org.w3.vs.assertor.{ LocalValidators, FromHttpResponseAssertor }

class StrategyTest extends WordSpec with MustMatchers with FilterMatchers {

  val validatorNu: FromHttpResponseAssertor = LocalValidators.ValidatorNu

  val strategy =
    Strategy(
      entrypoint = URL("http://example.com/foo"),
      linkCheck = true,
      maxResources = 100,
      filter = Filter.includeEverything,
      assertorsConfiguration = AssertorsConfiguration.default)

  "a strategy must select the assertors based on their mimetype" in {

    val httpResponseKnownMimeType =
      HttpResponse(
        url = URL("http://example.com/foo"),
        method = GET,
        status = 200,
        headers = Headers(Map("Content-Type" -> List("text/html"))),
        extractedURLs = List.empty,
        doctypeOpt = None)

    strategy.getAssertors(httpResponseKnownMimeType) must contain(validatorNu)

  }

  "a strategy must reject assertors for unknown mimetype" in {

    val httpResponseKnownMimeType =
      HttpResponse(
        url = URL("http://example.com/foo"),
        method = GET,
        status = 200,
        headers = Headers(Map("Content-Type" -> List("unknown"))),
        extractedURLs = List.empty,
        doctypeOpt = None)

    strategy.getAssertors(httpResponseKnownMimeType) must be('empty)

  }

  "a strategy must return assertors only for responses in the 2xx range status" in {

    val httpResponseKnownMimeType =
      HttpResponse(
        url = URL("http://example.com/foo"),
        method = GET,
        status = 404,
        headers = Headers(Map("Content-Type" -> List("text/html"))),
        extractedURLs = List.empty,
        doctypeOpt = None)

    strategy.getAssertors(httpResponseKnownMimeType) must be('empty)

  }

  "a strategy must return assertors only for responses from a GET" in {

    val httpResponseKnownMimeType =
      HttpResponse(
        url = URL("http://example.com/foo"),
        method = HEAD,
        status = 200,
        headers = Headers(Map("Content-Type" -> List("text/html"))),
        extractedURLs = List.empty,
        doctypeOpt = None)

    strategy.getAssertors(httpResponseKnownMimeType) must be('empty)

  }

}
