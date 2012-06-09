package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import org.w3.util.Headers.wrapHeaders
import org.w3.vs.http._
import org.joda.time._
import scalaz.Equal
import org.w3.banana._

object ResourceResponse {

  def apply(vo: ResourceResponseVO)(implicit conf: VSConfiguration): ResourceResponse = vo match {
    case er: ErrorResponseVO => ErrorResponse(er)
    case hr: HttpResponseVO => HttpResponse(hr)
  }

  def getResourceResponseVO(id: ResourceResponseId)(implicit conf: VSConfiguration): FutureVal[Exception, ResourceResponseVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = ResourceResponseUri(id)
    FutureVal.applyTo(conf.store.getNamedGraph(uri)) flatMapValidation { graph => 
      val pointed = PointedGraph(uri, graph)
      ResourceResponseVOBinder.fromPointedGraph(pointed)
    }
  }
  
  def get(id: ResourceResponseId)(implicit conf: VSConfiguration): FutureVal[Exception, ResourceResponse] =
    getResourceResponseVO(id) map { ResourceResponse(_) }

  def getForJob(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")

  def getForRun(id: RunId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[ResourceResponse]] = sys.error("")

  def saveResourceResponseVO(vo: ResourceResponseVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = ResourceResponseVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(ResourceResponseUri(vo.id), graph)
    FutureVal.toFutureValException(FutureVal.applyTo(result))
  }

  def save(rr: ResourceResponse)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveResourceResponseVO(rr.toValueObject)

  def delete(resource: ResourceResponse)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = sys.error("")

}

sealed trait ResourceResponse {
  
  implicit val conf: VSConfiguration
  
  val id: ResourceResponseId
  val jobId: JobId
  val runId: RunId
  val url: URL
  val action: HttpAction
  val timestamp: DateTime
  
  def getJob(): FutureVal[Exception, Job] = Job.get(jobId)

  def getRun(): FutureVal[Exception, Run] = Run.get(runId)
  
  def save(): FutureVal[Exception, Unit] = ResourceResponse.save(this)

  def delete(): FutureVal[Exception, Unit] = ResourceResponse.delete(this)
  
  def toValueObject: ResourceResponseVO

  //def toTinyString: String = "[%s/%s\t%s\t%s\t%s" format (jobId.shortId, runId.shortId, action.toString, url.toString, timestamp.toString())

}




object ErrorResponse {

  def apply(vo: ErrorResponseVO)(implicit conf: VSConfiguration): ErrorResponse = {
    import vo._
    ErrorResponse(id, jobId, runId, url, action, timestamp, why)
  }

}

case class ErrorResponse(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    why: String)(implicit val conf: VSConfiguration) extends ResourceResponse {
  
  def toValueObject: ErrorResponseVO = ErrorResponseVO(id, jobId, runId, url, action, timestamp, why)
}



object HttpResponse {

  def apply(vo: HttpResponseVO)(implicit conf: VSConfiguration): HttpResponse = {
    import vo._
    HttpResponse(id, jobId, runId, url, action, timestamp, status, headers, extractedURLs)
  }

  def apply(
      jobId: JobId,
      runId: RunId,
      url: URL,
      action: HttpAction,
      status: Int,
      headers: Headers,
      body: String)(implicit conf: VSConfiguration): HttpResponse = {
    
    val extractedURLs = headers.mimetype collect {
      case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct
      case "text/css" => URLExtractor.fromCSS(url, body).distinct
    } getOrElse List.empty
    
    HttpResponse(jobId = jobId, runId = runId, url = url, action = action, status = status, headers = headers, extractedURLs = extractedURLs)
  }

}

case class HttpResponse(
    id: ResourceResponseId = ResourceResponseId(),
    jobId: JobId,
    runId: RunId,
    url: URL,
    action: HttpAction,
    timestamp: DateTime = DateTime.now(DateTimeZone.UTC),
    status: Int,
    headers: Headers,
    extractedURLs: List[URL])(implicit val conf: VSConfiguration) extends ResourceResponse {
  
  def toValueObject: HttpResponseVO = HttpResponseVO(id, jobId, runId, url, action, timestamp, status, headers, extractedURLs)
}

