package org.w3.vs

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Mode._

object Global extends GlobalSettings with Rendering with AcceptExtractors {

  var conf: ValidatorSuite = _

  override def beforeStart(app: Application): Unit = {

    assert(conf == null)

    app.mode match {

      case Prod => conf = new ValidatorSuite with Graphite {
        val mode = Prod
      }

      case m @ (Test | Dev) => conf = new ValidatorSuite {
        val mode = m
      }

    }

  }

  override def onStart(app: Application): Unit = {
    //conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    conf.start()
    org.w3.vs.assertor.LocalValidators.start()
    org.w3.vs.model.Job.resumeAllJobs()(conf)
  }
  
  override def onStop(app: Application): Unit = {
    //ResponseCache.setDefault(null)
    //org.w3.vs.assertor.LocalValidators.stop()
    conf.shutdown()
    conf = null
  }

  override def onHandlerNotFound(request : RequestHeader) : Result = {
    implicit val implReq = request
    render {
      case Accepts.Html() => NotFound(views.html.error._404())
      case Accepts.Json() => NotFound
    }
  }

  override def onError(request: RequestHeader, ex: Throwable): Result = {
    implicit val implReq = request
    render {
      case Accepts.Html() => InternalServerError(views.html.error.generic(messages = List(("error", ex.getMessage))))
      case Accepts.Json() => InternalServerError
    }
  }

  override def onBadRequest(request: RequestHeader, error: String) = {
    implicit val implReq = request
    error match {
      case "InvalidJobId" => onHandlerNotFound(request)
      case _ => BadRequest(views.html.error.generic(List(("error", "Bad Request: " + error))))
    }
  }

}
