package org.w3.vs.actor

import org.w3.vs.model._
import org.w3.vs.assertor._

case class AssertorCall(runId: RunId, assertor: FromHttpResponseAssertor, response: HttpResponse) {
  override def toString = s"AssertorCall[${runId.shortId} ${assertor.id} ${response.url}} assertor]"
}
