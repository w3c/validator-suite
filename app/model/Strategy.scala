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
    maxResources: Int) {

  def mainAuthority: Authority = entrypoint.authority

  def getActionFor(url: URL): HttpAction = {
    if (url.toString.startsWith(entrypoint.toString)) {
      if (url.authority === entrypoint.authority)
        GET
      else
        IGNORE
    } else {
      IGNORE
    }
  }

  // TODO revise how this is done
  import org.w3.vs.assertor._
  def getAssertors(httpResponse: HttpResponse): Iterable[FromHttpResponseAssertor] = {
    for {
      mimetype <- httpResponse.headers.mimetype.toIterable
      if httpResponse.method === GET && httpResponse.status < 300 && httpResponse.status >= 200
      assertors <- AssertorsConfiguration.default.keys.map(Assertor.getById).filter(_.supportedMimeTypes.contains(mimetype))
    } yield assertors
  }

}
