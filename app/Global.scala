import play.api._

import java.net.{ Socket, ResponseCache }
import com.yammer.metrics._
import com.yammer.metrics.reporting._
import java.util.concurrent.TimeUnit

object Global extends GlobalSettings {

  val conf = org.w3.vs.Prod.configuration
  import conf._

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

    // Run the sample job on start
    /*import org.w3.vs.model.Job
    implicit val configuration: org.w3.vs.VSConfiguration = org.w3.vs.Prod.configuration
    import scala.concurrent.ExecutionContext.Implicits.global
    Job.get(Job.sample.id).map(_.run())*/

  }
  
  override def onStop(app: Application): Unit = {
    Metrics.shutdown()
    ResponseCache.setDefault(null)
    org.w3.vs.assertor.LocalValidators.stop()
    system.shutdown()
  }
  
}
