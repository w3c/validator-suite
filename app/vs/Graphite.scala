package org.w3.vs

import com.codahale.metrics._
import com.codahale.metrics.graphite.{ Graphite => MGraphite, GraphiteReporter }
import java.util.concurrent.TimeUnit
import play.api.Mode

trait Graphite extends ValidatorSuite { this: ValidatorSuite with Database =>

  lazy val graphiteConf = config.getConfig("application.graphite-reporter") getOrElse sys.error("application.graphite-reporter")

  val r = """^(\d+)([^\d]+)$""".r
  lazy val (period, unit) =
    graphiteConf.getString("period") getOrElse sys.error("period") match {
      case r(period, "s") => (period.toInt, TimeUnit.SECONDS)
    }
  lazy val host = graphiteConf.getString("host") getOrElse sys.error("host")
  lazy val port = graphiteConf.getInt("port") getOrElse sys.error("port")
  lazy val prefix = graphiteConf.getString("prefix") getOrElse sys.error("prefix")

  val graphite: MGraphite = new MGraphite(new java.net.InetSocketAddress(host, port))

  private val reporter = {
    GraphiteReporter
      .forRegistry(Metrics.metrics)
      .prefixedWith(prefix) /* should be the server being used! */
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .filter(MetricFilter.ALL)
      .build(graphite)

    /*ConsoleReporter.forRegistry(Metrics.metrics)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()*/
  }

  override def start() {
    super.start()
    logger.info("Initializing Graphite")
    reporter.start(1, TimeUnit.MINUTES)
    Metrics.metrics.register("db.jobs", Metrics.db.jobs()(this))
    Metrics.metrics.register("db.users", Metrics.db.users()(this))
  }

  override def shutdown() {
    logger.info("Shutting down Graphite")
    reporter.stop()
    graphite.close()
    super.shutdown()
  }

}
