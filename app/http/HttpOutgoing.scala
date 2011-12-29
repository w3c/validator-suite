package org.w3.vs.http

import org.w3.util._

sealed trait HttpOutgoing
case class GETResponse(status: Int, headers: Headers, body: String) extends HttpOutgoing
case class HEADResponse(status: Int, headers: Headers) extends HttpOutgoing
