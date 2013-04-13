package org.w3.vs

import com.yammer.metrics._
import com.yammer.metrics.reporting._
import java.net.ResponseCache
import java.util.concurrent.TimeUnit
import org.w3.vs.model.Job
import play.api._


object Global extends GlobalSettings {

  var conf: ValidatorSuite
    with ActorSystem
    with Database
    with HttpClient
    with RunEvents = _

  override def beforeStart(app: Application): Unit = {

    assert(conf == null)

    conf = new ValidatorSuite(mode = app.mode)
      with DefaultActorSystem
      with DefaultDatabase
      with DefaultHttpClient
      with DefaultRunEvents
  }

  override def onStart(app: Application): Unit = {

    // TODO: that's ugly
    val graphiteConf =
      Configuration.load(new java.io.File(".")).getConfig("application.graphite-reporter") getOrElse sys.error("application.graphite-reporter")
    if (graphiteConf.getBoolean("enable") getOrElse false) {
      val r = """^(\d+)([^\d]+)$""".r
      val (period, unit) =
        graphiteConf.getString("period") getOrElse sys.error("period") match {
          case r(period, "s") => (period.toInt, TimeUnit.SECONDS)
        }
      val host = graphiteConf.getString("host") getOrElse sys.error("host")
      val port = graphiteConf.getInt("port").map(_.toInt) getOrElse sys.error("port")
      val prefix = graphiteConf.getString("prefix") getOrElse sys.error("prefix")
      GraphiteReporter.enable(period, unit, host, port, prefix)
    }
    conf.httpCacheOpt foreach { cache => ResponseCache.setDefault(cache) }
    org.w3.vs.assertor.LocalValidators.start()

    Job.resumeAllJobs()(conf)

  }
  
  override def onStop(app: Application): Unit = {
    Metrics.shutdown()
    ResponseCache.setDefault(null)
    conf.shutdown()
  }
  
}
