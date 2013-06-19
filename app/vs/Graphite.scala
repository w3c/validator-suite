package org.w3.vs

import com.yammer.metrics.reporting.GraphiteReporter
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit

trait Graphite extends ValidatorSuite {

  lazy val graphiteConf = config.getConfig("application.graphite-reporter") getOrElse sys.error("application.graphite-reporter")

  val r = """^(\d+)([^\d]+)$""".r
  lazy val (period, unit) =
    graphiteConf.getString("period") getOrElse sys.error("period") match {
      case r(period, "s") => (period.toInt, TimeUnit.SECONDS)
    }
  lazy val host = graphiteConf.getString("host") getOrElse sys.error("host")
  lazy val port = graphiteConf.getInt("port").map(_.toInt) getOrElse sys.error("port")
  lazy val prefix = graphiteConf.getString("prefix") getOrElse sys.error("prefix")

  override def start(): Unit = {
    super.start()
    logger.info("Initializing Graphite")
    GraphiteReporter.enable(period, unit, host, port, prefix)
  }

  override def shutdown() {
    logger.info("Shutting down Metrics")
    Metrics.shutdown()
    super.shutdown()
  }

}
