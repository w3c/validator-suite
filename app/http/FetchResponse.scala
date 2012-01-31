package org.w3.vs.http

import org.w3.util._
import org.w3.vs.model.FetchAction

sealed trait FetchResponse
case class OkResponse(url: URL, action: FetchAction, status: Int, headers: Headers, body: String) extends FetchResponse
case class KoResponse(url: URL, why: Throwable) extends FetchResponse
