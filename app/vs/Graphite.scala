package org.w3.vs

import _root_.controllers.Application
import com.codahale.metrics._
import com.codahale.metrics.graphite.{ Graphite => MGraphite, GraphiteReporter }
import java.util.concurrent.TimeUnit

object Graphite {

  val metrics = new MetricRegistry()

  val timer1 = Graphite.metrics.timer(MetricRegistry.name(Application.getClass, "timer1"))

}

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

  val graphite: MGraphite = new MGraphite(new java.net.InetSocketAddress(host, port))
  val reporter: GraphiteReporter =
    GraphiteReporter
      .forRegistry(Graphite.metrics)
      .prefixedWith(prefix) /* should be the server being used! */
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)

  override def start(): Unit = {
    super.start()
    logger.info("Initializing Graphite")
    reporter.start(1, TimeUnit.MINUTES);
  }

  override def shutdown() {
    logger.info("Shutting down Metrics")
    reporter.stop()
    super.shutdown()
  }

}
