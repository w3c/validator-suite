package org.w3.vs.http

import org.w3.util._
import org.w3.vs.model.HttpVerb

sealed trait FetchResponse
case class OkResponse(url: URL, action: HttpVerb, status: Int, headers: Headers, body: String) extends FetchResponse
case class KoResponse(url: URL, action: HttpVerb, why: Throwable) extends FetchResponse
