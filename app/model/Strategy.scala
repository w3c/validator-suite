package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import akka.dispatch._
import org.w3.banana._
import scalaz._
import scalaz.Scalaz._

object Strategy {
  val maxUrlsToFetch = 10
}

case class Strategy (
    entrypoint: URL,
    linkCheck: Boolean,
    maxResources: Int,
    filter: Filter = Filter.includeEverything,
    assertorsConfiguration: AssertorsConfiguration) {

  def mainAuthority: Authority = entrypoint.authority

  def getActionFor(url: URL): HttpAction = {
//  if (filter.passThrough(url)) {
    // Tom: filters are not persisted and too complicated anyway for our simple single use case
    if (url.toString.startsWith(entrypoint.toString)) {
      if (url.authority === entrypoint.authority)
        GET
      else if (linkCheck)
        HEAD
      else
        IGNORE
    } else {
      IGNORE
    }
  }

  // TODO revise how this is done
  import org.w3.vs.assertor._
  def getAssertors(httpResponse: HttpResponse): List[FromHttpResponseAssertor] = {
    for {
      mimetype <- httpResponse.headers.mimetype.toList if httpResponse.action === GET
      assertors <- assertorsConfiguration.keys.map(Assertor.getById).filter(_.supportedMimeTypes.contains(mimetype))
    } yield assertors
  }

}
