package org.w3.vs.model

import org.w3.util._
import org.joda.time._
import java.util.UUID
import org.w3.vs.http._
import org.w3.util.Headers.wrapHeaders

object ResourceInfo {

  // TODO: make jobId part of fetchResponse
  def fromFetchResponse(fetchResponse: FetchResponse, jobId: JobId): ResourceInfo = {
    fetchResponse match {
      case OkResponse(url, action, status, headers, body, runId) => {
        val extractedURLs = headers.mimetype collect {
          case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct
          case "text/css" => URLExtractor.fromCSS(url, body).distinct
        } getOrElse List.empty
        val ri = ResourceInfo(
          url = url,
          jobId = jobId,
          runId = runId,
          action = action,
          result = FetchResult(status, headers, extractedURLs))
        ri
      }
      case KoResponse(url, action, why, runId) => {
        val ri = ResourceInfo(
          url = url,
          jobId = jobId,
          runId = runId,
          action = action,
          result = ResourceInfoError(why.getMessage))
        ri
      }
    }
  }

}

case class ResourceInfo(
    id: ResourceInfo#Id = UUID.randomUUID(),
    url: URL,
    jobId: JobId,
    runId: RunId,
    action: HttpVerb,
    timestamp: DateTime = new DateTime,
    result: ResourceInfoResult) {
  type Id = UUID
  
  def toTinyString: String = "[%s/%s\t%s\t%s\t%s" format (jobId.shortId, runId.shortId, action.toString, url.toString, timestamp.toString())
  
}



sealed trait ResourceInfoResult

case class ResourceInfoError(why: String) extends ResourceInfoResult

case class FetchResult(
    status: Int,
    headers: Headers,
    extractedLinks: List[URL]) extends ResourceInfoResult

