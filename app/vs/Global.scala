package org.w3.vs

import play.api._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Mode._
import java.net.ResponseCache

object Global extends GlobalSettings {

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
    NotFound(views.html.error._404())
  }

}
