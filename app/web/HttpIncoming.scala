package org.w3.vs.web

import org.w3.vs.util.URL

private[web] sealed trait HttpIncoming
private[web] case class HttpGET(u: URL) extends HttpIncoming
private[web] case class HttpHEAD(u: URL) extends HttpIncoming
