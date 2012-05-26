package org.w3.vs.model

import org.w3.util._
import org.w3.util.Headers.wrapHeaders
import org.w3.vs.http._
import org.joda.time._
import scalaz.Equal

sealed trait ResourceResponse {
  
  val id: ResourceResponseId
  val jobId: JobId
  val runId: RunId
  val url: URL
  val action: HttpAction
  val timestamp: DateTime
  
  def getJob: FutureVal[Exception, Job] = Job.get(jobId)
  def getRun: FutureVal[Exception, Run] = Run.get(runId)
  
  def save(): FutureVal[Exception, ResourceResponse] = ResourceResponse.save(this)
  def delete(): FutureVal[Exception, Unit] = ResourceResponse.delete(this)
  
  //def toTinyString: String = "[%s/%s\t%s\t%s\t%s" format (valueObject.jobId.shortId, valueObject.runId.shortId, action.toString, url.toString, timestamp.toString())
}

object ResourceResponse {
  
  def get(id: ResourceResponseId): FutureVal[Exception, ResourceResponse] = sys.error("")
  def getForJob(id: JobId): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")
  def getForRun(id: RunId): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")
  def save(resource: ResourceResponse): FutureVal[Exception, ResourceResponse] = sys.error("")
  def delete(resource: ResourceResponse): FutureVal[Exception, Unit] = sys.error("")
}

case class ErrorResponse(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    why: String) extends ResourceResponse {
  
  def toValueObject: ErrorResponseVO = ErrorResponseVO(id, jobId, runId, url, action, timestamp, why)
  
}

case class HttpResponse(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now,
    status: Int,
    headers: Headers,
    extractedURLs: List[URL]) extends ResourceResponse {
  
  def toValueObject: HttpResponseVO = HttpResponseVO(id, jobId, runId, url, action, timestamp, status, headers, extractedURLs)
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
    
    HttpResponse(jobId = jobId, runId = runId, url = url, action = action, status = status, headers = headers, extractedURLs = extractedURLs)
    
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