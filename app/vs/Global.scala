package org.w3.vs


import play.api._
import java.net.ResponseCache


object Global extends GlobalSettings {

  var conf: ValidatorSuite
    with ActorSystem
    with Database
    with HttpClient
    with RunEvents = _

  override def beforeStart(app: Application): Unit = {

    assert(conf == null)

    import play.api.Mode._
    app.mode match {

      case Prod => conf = new ValidatorSuite(Prod)
        with DefaultActorSystem
        with DefaultDatabase
        with DefaultHttpClient
        with DefaultRunEvents
        with Graphite

      case mode @ (Test | Dev) => conf = new ValidatorSuite(mode)
        with DefaultActorSystem
        with DefaultDatabase
        with DefaultHttpClient
        with DefaultRunEvents

    }

  }

  override def onStart(app: Application): Unit = {
    //conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    org.w3.vs.assertor.LocalValidators.start()
    org.w3.vs.model.Job.resumeAllJobs()(conf)
  }
  
  override def onStop(app: Application): Unit = {
    //ResponseCache.setDefault(null)
    //org.w3.vs.assertor.LocalValidators.stop()
    conf.shutdown()
  }
  
}
