package org.w3.vs.http

import org.w3.util._
import org.w3.vs.model._

sealed trait FetchResponse {
  val url: URL
  val action: HttpVerb
  val runId: RunId
}

case class OkResponse(url: URL, action: HttpVerb, status: Int, headers: Headers, body: String, runId: RunId) extends FetchResponse

case class KoResponse(url: URL, action: HttpVerb, why: Throwable, runId: RunId) extends FetchResponse
