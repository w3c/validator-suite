package org.w3.vs.actor

import org.w3.vs.model._
import org.w3.vs.assertor._

case class AssertorCall(context: Run.Context, assertor: FromHttpResponseAssertor, response: HttpResponse) {
  override def toString = s"AssertorCall[${context.userId.shortId}/${context.jobId.shortId} ${assertor.id} ${response.url}} assertor]"
}
