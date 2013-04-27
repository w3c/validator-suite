package org.w3.vs

import com.yammer.metrics.reporting.GraphiteReporter
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit

trait Graphite extends ValidatorSuite {
  this: ValidatorSuite =>

  logger.info("Initializing Graphite")

  val graphiteConf = config.getConfig("application.graphite-reporter") getOrElse sys.error("application.graphite-reporter")

  //if (graphiteConf.getBoolean("enable") getOrElse false) {
  val r = """^(\d+)([^\d]+)$""".r
  val (period, unit) =
    graphiteConf.getString("period") getOrElse sys.error("period") match {
      case r(period, "s") => (period.toInt, TimeUnit.SECONDS)
    }
  val host = graphiteConf.getString("host") getOrElse sys.error("host")
  val port = graphiteConf.getInt("port").map(_.toInt) getOrElse sys.error("port")
  val prefix = graphiteConf.getString("prefix") getOrElse sys.error("prefix")
  //}

  GraphiteReporter.enable(period, unit, host, port, prefix)

  override def shutdown() {
    logger.info("Shuting down Metrics")
    Metrics.shutdown()
    super.shutdown()
  }

}
