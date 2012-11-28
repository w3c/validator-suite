package org.w3.vs.actor

import org.w3.vs.model._
import org.w3.vs.assertor._

case class AssertorCall(context: (UserId, JobId, RunId), assertor: FromHttpResponseAssertor, response: HttpResponse) {
  override def toString = "AssertorCall[%s/%s assertor." format (context._2.shortId, context._3.shortId, assertor.name, response.url)
}
