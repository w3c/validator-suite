package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import org.w3.vs._
import java.io._
import scalax.io._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

object ResourceResponse {

  def getFor(runId: RunId)(implicit conf: VSConfiguration): Future[Set[ResourceResponse]] = {
    import conf._
    val query = Json.obj("_id" -> toJson(runId))
    val cursor = Run.collection.find[JsValue, JsValue](query)
    cursor.toList map { list =>
      val json = list.headOption.get
      val events = (json \ "events").as[Set[RunEvent]]
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
