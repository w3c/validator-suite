package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.util._
import org.w3.vs.web._
import akka.dispatch._
import scalaz._
import scalaz.Scalaz._

object Strategy {
  val maxUrlsToFetch = 10
}

case class Strategy (
    entrypoint: URL,
    maxResources: Int,
    linkCheck: Boolean = false,
    filter: Filter = Filter.includeEverything,
    assertorsConfiguration: AssertorsConfiguration = AssertorsConfiguration.default) {

  def mainAuthority: Authority = entrypoint.authority

  def getActionFor(url: URL): HttpAction = {
//  if (filter.passThrough(url)) {
    // Tom: filters are not persisted and too complicated anyway for our simple single use case
    if (url.authority === entrypoint.authority)
      GET
    else if (linkCheck)
      HEAD
    else
      IGNORE
  }

  // TODO revise how this is done
  import org.w3.vs.assertor._
  def getAssertors(httpResponse: HttpResponse): Iterable[FromHttpResponseAssertor] = {
    for {
      mimetype <- httpResponse.headers.mimetype.toIterable
      if httpResponse.method === GET && httpResponse.status < 300 && httpResponse.status >= 200
      assertors <- assertorsConfiguration.keys.map(Assertor.getById).filter(_.supportedMimeTypes.contains(mimetype))
    } yield assertors
  }

}
