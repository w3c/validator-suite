package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.banana._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import ops._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import java.io._
import scalax.io._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

object ResourceResponse {

  def getFor(userId: UserId, jobId: JobId, runId: RunId)(implicit conf: VSConfiguration): Future[Set[ResourceResponse]] = {
    import conf._
    getFor((userId, jobId, runId).toUri)
  }

  def getFor(runUri: Rdf#URI)(implicit conf: VSConfiguration): Future[Set[ResourceResponse]] = {
    import conf._
    for {
      ldr <- store.asLDStore.GET(runUri)
      events <- (ldr.resource / ont.event).asSet[RunEvent].asFuture
    } yield {
      events collect { case ResourceResponseEvent(rr, _) => rr }
    }
  }

}

sealed trait ResourceResponse {
  val url: URL
  val method: HttpMethod
}

case class ErrorResponse(
    url: URL,
    method: HttpMethod,
    why: String) extends ResourceResponse

object HttpResponse {

  def apply(
      url: URL,
      method: HttpMethod,
      status: Int,
      headers: Headers,
      resource: InputResource[InputStream]): HttpResponse = {
    
    val extractedURLs: List[URL] = headers.mimetype collect {
      case "text/html" | "application/xhtml+xml" => html.HtmlParser.parse(url, resource, headers.charset).map(URL.clearHash).distinct
      case "text/css" => List.empty // TODO
    } getOrElse List.empty
    
    HttpResponse(url = url, method = method, status = status, headers = headers, extractedURLs = extractedURLs)
  }

}

case class HttpResponse(
    url: URL,
    method: HttpMethod,
    status: Int,
    headers: Headers,
    extractedURLs: List[URL]) extends ResourceResponse
