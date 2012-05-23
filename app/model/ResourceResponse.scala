package org.w3.vs.model

import org.w3.util._
import org.w3.util.Headers.wrapHeaders
import org.w3.vs.http._
import org.joda.time._
import scalaz.Equal

sealed trait ResourceResponseVO {
  val id: ResourceResponseId
  val jobId: JobId
  val runId: RunId
  val url: URL
  val action: HttpAction
  val timestamp: DateTime
}

case class ErrorResponseVO(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    why: String) extends ResourceResponseVO
    
case class HttpResponseVO(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    status: Int,
    headers: Headers,
    extractedURLs: List[URL]) extends ResourceResponseVO

object ResourceResponse {
  def get(id: ResourceResponseId): FutureVal[Exception, ResourceResponse] = sys.error("")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")
  def getForRun(id: RunId): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")
  def save(resource: ResourceResponse): FutureVal[Exception, ResourceResponse] = sys.error("")
}

sealed trait ResourceResponse {
  val valueObject: ResourceResponseVO
  
  def id: ResourceResponseId = valueObject.id
  def jobId: JobId = valueObject.jobId
  def runId: RunId = valueObject.runId
  def url: URL = valueObject.url
  def action: HttpAction = valueObject.action
  def timestamp: DateTime = valueObject.timestamp
  
  def getJob: FutureVal[Exception, Job] = Job.get(valueObject.jobId)
  def getRun: FutureVal[Exception, Run] = Run.get(valueObject.runId)
  
  def save(): FutureVal[Exception, ResourceResponse] = ResourceResponse.save(this)
  
  def toTinyString: String = "[%s/%s\t%s\t%s\t%s" format (valueObject.jobId.shortId, valueObject.runId.shortId, action.toString, url.toString, timestamp.toString())
}

case class ErrorResponse(valueObject: ErrorResponseVO) extends ResourceResponse {
  def why: String = valueObject.why
}

object ErrorResponse {
  
  def apply(
      id: ResourceResponseId = ResourceResponseId(),
      jobId: JobId,
      runId: RunId,
      url: URL,
      action: HttpAction,
      timestamp: DateTime = DateTime.now,
      why: String): ErrorResponse = 
   ErrorResponse(ErrorResponseVO(id, jobId, runId, url, action, timestamp, why))
  
}

case class HttpResponse(valueObject: HttpResponseVO) extends ResourceResponse {
  def status: Int = valueObject.status
  def headers: Headers = valueObject.headers 
  def extractedURLs: List[URL] = valueObject.extractedURLs 
}

object HttpResponse {
    
    def apply(
      jobId: JobId,
      runId: RunId,
      url: URL,
      action: HttpAction,
      status: Int,
      headers: Headers,
      body: String): HttpResponse = {
    
    val extractedURLs = headers.mimetype collect {
      case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct
      case "text/css" => URLExtractor.fromCSS(url, body).distinct
    } getOrElse List.empty
    
    HttpResponse(HttpResponseVO(jobId = jobId, runId = runId, url = url, action = action, status = status, headers = headers, extractedURLs = extractedURLs))
    
  }
    
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