package org.w3.vs.model

import org.w3.util._
import org.w3.util.Headers.wrapHeaders
import org.w3.vs.http._
import org.joda.time._
import scalaz.Equal

object Response {

  // TODO: make jobId part of fetchResponse
//  def fromResponse(fetchResponse: Response, jobId: JobId): ResourceInfo = {
//    fetchResponse match {
//      case HttpResponse(url, action, status, headers, body, runId) => {
//        val extractedURLs = headers.mimetype collect {
//          case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct
//          case "text/css" => URLExtractor.fromCSS(url, body).distinct
//        } getOrElse List.empty
//        val ri = FetchResult(FetchResultVO(
//          jobId = jobId,
//          runId = runId,
//          url = url,
//          action = action,
//          status = status,
//          headers = headers,
//          extractedURLs = extractedURLs))
//        ri
//      }
//      case ErrorResponse(url, action, why, runId) => {
//        val ri = ResourceError(ResourceErrorVO(
//          jobId = jobId,
//          runId = runId,
//          url = url,
//          action = action,
//          why = why.getMessage))
//        ri
//      }
//    }
//  }

}

sealed trait ResponseVO {
  val id: ResponseId = ResponseId()
  val jobId: JobId
  val runId: RunId
  val url: URL
  val action: HttpAction
  val timestamp: DateTime = DateTime.now
}

case class ErrorResponseVO(
    id: ResponseId = ResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    why: String) extends ResponseVO
    
case class HttpResponseVO(
    id: ResponseId = ResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    status: Int,
    headers: Headers,
    extractedLinks: List[URL]) extends ResponseVO


sealed trait Response {
  val valueObject: ResponseVO
  
  def id: ResponseId = valueObject.id
  def url: URL = valueObject.url
  def action: HttpAction = valueObject.action
  def timestamp: DateTime = valueObject.timestamp
  
  def getJob: FutureVal[Exception, Job] = Job.get(valueObject.jobId)
  def getRun: FutureVal[Exception, Run] = Run.get(valueObject.runId)
  
  def toTinyString: String = "[%s/%s\t%s\t%s\t%s" format (valueObject.jobId.shortId, valueObject.runId.shortId, action.toString, url.toString, timestamp.toString())
}

case class ErrorResponse(valueObject: ErrorResponseVO) extends Response {
  def why: String = valueObject.why
}

case class HttpResponse(valueObject: HttpResponseVO) extends Response {
  def status: Int = valueObject.status
  def headers: Headers = valueObject.headers 
  def extractedLinks: List[URL] = valueObject.extractedLinks 
} 


sealed trait HttpAction
case object IGNORE extends HttpAction
case object GET extends HttpAction
case object HEAD extends HttpAction

object HttpAction {
  implicit val equalHttpAction: Equal[HttpAction] = new Equal[HttpAction] {
    def equal(left: HttpAction, right: HttpAction): Boolean = left == right
  }
}