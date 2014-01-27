package org.w3.vs

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Mode._
import concurrent.Future

object Global extends GlobalSettings with Rendering with AcceptExtractors {

  var vs: ValidatorSuite with EmailService = _

  override def beforeStart(app: Application): Unit = {

    assert(vs == null)

    app.mode match {

      case Prod => vs = new ValidatorSuite with Graphite with EmailService {
        val mode = Prod
      }

      case m @ (Test | Dev) => vs = new ValidatorSuite with Graphite with EmailService {
        val mode = m
      }

    }

  }

  override def onStart(app: Application): Unit = {
    //conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    vs.start()
    //org.w3.vs.assertor.LocalValidators.start()
    org.w3.vs.model.Job.resumeAllJobs()(vs)
  }
  
  override def onStop(app: Application): Unit = {
    //ResponseCache.setDefault(null)
    //org.w3.vs.assertor.LocalValidators.stop()
    vs.shutdown()
    vs = null
  }

  override def onHandlerNotFound(request : RequestHeader): Future[SimpleResult] = {
    implicit val implReq = request
    Metrics.errors.e404()
    Future.successful(render {
      case Accepts.Html() => NotFound(views.html.error._404())
      case Accepts.Json() => NotFound
    })
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[SimpleResult] = {
    implicit val implReq = request
    Metrics.errors.e500()
    Future.successful(render {
      case Accepts.Html() => InternalServerError(views.html.error._500(List(("error", ex.getMessage))))
      case Accepts.Json() => InternalServerError
    })
  }

  override def onBadRequest(request: RequestHeader, error: String): Future[SimpleResult] = {
    implicit val implReq = request
    error match {
      case "InvalidJobId" => onHandlerNotFound(request)
      case _ => {
        Metrics.errors.e400()
        Future.successful(BadRequest(views.html.error._400(List(("error", error)))))
      }
    }
  }

}
