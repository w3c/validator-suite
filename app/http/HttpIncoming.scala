package org.w3.vs.http

import org.w3.util.URL

private[http] sealed trait HttpIncoming
private[http] case class HttpGET(u: URL) extends HttpIncoming
private[http] case class HttpHEAD(u: URL) extends HttpIncoming
