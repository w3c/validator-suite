package org.w3.vs

import akka.actor._
import org.w3.vs.http._
import org.w3.vs.actor._
import org.w3.util.Util
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import java.util.concurrent.ForkJoinPool
import scala.concurrent.ExecutionContext.Implicits.global
import com.ning.http.client.{ AsyncHttpClientConfig, AsyncHttpClient }
import play.api.Configuration
import java.io.File
import reactivemongo.api.MongoConnection

object DefaultProdConfiguration {

  val logger = play.Logger.of(classOf[VSConfiguration])

}

trait DefaultProdConfiguration extends VSConfiguration {

  val configuration = Configuration.load(new File("."))
  
  /**
   * note: an AsyncHttpClient is a heavy object with a thread
   * and connection pool associated with it, it's supposed to
   * be shared among lots of requests, not per-http-request
   */
  val httpClient = {
    // in future version of Typesafe's Config: s/getConfig/atPath/
    val httpClientConf = configuration.getConfig("application.http-client") getOrElse sys.error("application.http-client")
//    val executor = new ForkJoinPool()
    val maxConnectionsTotal = httpClientConf.getInt("maximum-connections-total") getOrElse sys.error("maximum-connections-total")
    val maxConnectionsPerHost = httpClientConf.getInt("maximum-connectionsper-host") getOrElse sys.error("maximum-connectionsper-host")
    val timeout = httpClientConf.getInt("timeout") getOrElse sys.error("timeout")
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder
        // no redirect, we handle them in the crawler ourselved
        .setFollowRedirects(false)
        // concurrent connections
        .setMaximumConnectionsTotal(maxConnectionsTotal)
        .setMaximumConnectionsPerHost(maxConnectionsPerHost)
        // looks like there is a big issue when targetting w3.org using a custom executor
        // .setExecutorService(executor)
        // timeouts
        .setIdleConnectionTimeoutInMs(timeout)
        .setIdleConnectionInPoolTimeoutInMs(timeout)
        .setRequestTimeoutInMs(timeout)
        .setWebSocketIdleTimeoutInMs(timeout)
        .setConnectionTimeoutInMs(timeout)
        .build
    new AsyncHttpClient(config)
  }

  val httpCacheOpt: Option[Cache] = Cache(configuration)

  implicit val system: ActorSystem = {
    val vs = ActorSystem("vs", configuration.getConfig("application.vs").map(_.underlying) getOrElse sys.error("application.vs"))
    val listener = vs.actorOf(Props(new Actor {
      def receive = {
        case d: DeadLetter ⇒ DefaultProdConfiguration.logger.debug("DeadLetter - sender: %s, recipient: %s, message: %s" format(d.sender.toString, d.recipient.toString, d.message.toString))
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }

  val runEventBus: RunEventBus = {
    val actorRef = system.actorOf(Props(new RunEventBusActor()(this)), "runevent-bus")
    RunEventBus(actorRef)
  }

  val runsActorRef: ActorRef =
    system.actorOf(Props(new RunsActor()(this)), "runs")

  val httpActorRef: ActorRef =
    system.actorOf(Props(new Http(httpClient, system.scheduler, httpCacheOpt)).withDispatcher("http-dispatcher"), "http")


  implicit val timeout: Timeout = {
    val r = """^(\d+)([^\d]+)$""".r
    val r(timeoutS, unitS) = configuration.getString("application.timeout") getOrElse sys.error("application.timeout")
    Timeout(Duration(timeoutS.toInt, unitS))
  }

  lazy val connection = {
    val driver = new reactivemongo.api.MongoDriver
    val node = configuration.getString("application.mongodb.node") getOrElse sys.error("application.mongodb.node")
    driver.connection(Seq(node))
  }

  lazy val db = {
    val dbName = configuration.getString("application.mongodb.db-name") getOrElse sys.error("application.mongodb.db-name")
    connection(dbName)(system.dispatchers.lookup("reactivemongo-dispatcher"))
  }

}
