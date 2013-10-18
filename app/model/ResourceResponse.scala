package org.w3.vs.model

import org.w3.vs.util._
import org.w3.vs.web._
import org.joda.time._
import org.w3.vs._
import java.io._
import scalax.io._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import play.modules.reactivemongo.json.ImplicitBSONHandlers._

// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats.{ RunIdFormat, ResourceResponseEventFormat }

object ResourceResponse {

  def getFor(runId: RunId)(implicit conf: ValidatorSuite): Future[Set[ResourceResponse]] = {
    import conf._
    val query = Json.obj(
      "runId" -> toJson(runId),
      "event" -> toJson("resource-response") )
    val cursor = Run.collection.find(query).cursor[JsValue]
    cursor.toList() map { list =>
      list.map(json => json.as[ResourceResponseEvent](ResourceResponseEventFormat).rr).toSet
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

    val (extractedURLs, doctypeOpt) = headers.mimetype collect {
      case "text/html" | "application/xhtml+xml" => {
        val extractLinks = status == 200
        val (urls, doctypeOpt) = HtmlParser.parse(url, resource, headers.charset, extractLinks)
        (urls.map(URL.clearHash).distinct, doctypeOpt)
      }
      case "text/css" => (List.empty, None) // TODO
    } getOrElse (List.empty, None)
    
    HttpResponse(url = url, method = method, status = status, headers = headers, extractedURLs = extractedURLs, doctypeOpt)
  }

}

case class HttpResponse(
  url: URL,
  method: HttpMethod,
  status: Int,
  headers: Headers,
  extractedURLs: List[URL],
  doctypeOpt: Option[Doctype]) extends ResourceResponse
