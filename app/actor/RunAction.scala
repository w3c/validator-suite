package org.w3.vs.model

import org.w3.vs.web.URL
import org.w3.vs.model._
import org.w3.vs.assertor._

sealed trait RunAction

case class AssertorCall(
  runId: RunId,
  assertor: FromHttpResponseAssertor,
  response: HttpResponse) extends RunAction {

  override def toString =
    s"AssertorCall[${runId.shortId} ${assertor.id} ${response.url}} assertor]"

}

/**
 * a Fetch command message for an Http actor
 *
 * The type for token could be anything (the Http actor just passes it to the
 * response) but this gives little type-safety
 *
 * @param url the URL of the Web resource to fetch
 * @param method the kind of HTTP action
 */
case class Fetch(url: URL, method: HttpMethod) extends RunAction

case class EmitEvent(event: RunEvent) extends RunAction
