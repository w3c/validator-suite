package org.w3.vs

import akka.actor._
import org.w3.vs.http._
import org.w3.vs.actor._
import org.w3.util.Util
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import java.util.concurrent.{ Executors, ExecutorService }
import scala.concurrent.ExecutionContext.Implicits.global
import com.ning.http.client.{ AsyncHttpClientConfig, AsyncHttpClient }
import org.w3.banana._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph
import play.api.Configuration
import java.io.File

trait DefaultProdConfiguration extends VSConfiguration {

  val configuration = Configuration.load(new File("."))
  
  val assertorExecutionContext: ExecutionContext = {
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }

  /**
   * note: an AsyncHttpClient is a heavy object with a thread
   * and connection pool associated with it, it's supposed to
   * be shared among lots of requests, not per-http-request
   */
  val httpClient = {
    // in future version of Typesafe's Config: s/getConfig/atPath/
    val httpClientConf = configuration.getConfig("application.http-client") getOrElse sys.error("application.http-client")
    val executor = Executors.newCachedThreadPool()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder.setMaximumConnectionsTotal(httpClientConf.getInt("maximum-connections-total") getOrElse sys.error("maximum-connections-total"))
      .setMaximumConnectionsPerHost(httpClientConf.getInt("maximum-connectionsper-host") getOrElse sys.error("maximum-connectionsper-host"))
      .setExecutorService(executor)
      .setFollowRedirects(false)
      .setConnectionTimeoutInMs(httpClientConf.getInt("timeout") getOrElse sys.error("timeout"))
      .build
    new AsyncHttpClient(config)
  }

  val httpCacheOpt: Option[Cache] = {
    val httpCacheConf = configuration.getConfig("application.http-cache") getOrElse sys.error("application.http-cache")
    if (httpCacheConf.getBoolean("enable") getOrElse sys.error("enable")) {
      val directory = new File(httpCacheConf.getString("directory") getOrElse sys.error("directory"))
      if (httpCacheConf.getBoolean("clean-at-startup") getOrElse sys.error("clean-at-startup")) {
        if (directory.exists) Util.delete(directory)
        if (! directory.mkdir()) sys.error("could not create HTTP Cache directory " + directory.getAbsolutePath)
      }
      assert(directory.exists, "directory [" + directory.getAbsolutePath + "] for the HTTP Cache must exist")
      val cache = Cache(directory)
      Some(cache)
    } else {
      None
    }
  }

  implicit val system: ActorSystem = {
    val vs = ActorSystem("vs")
    vs.actorOf(Props(new OrganizationsActor()(this)), "organizations")
    vs.actorOf(Props(new Http(httpClient, vs.scheduler, httpCacheOpt)), "http")
    val listener = vs.actorOf(Props(new Actor {
      val logger = play.Logger.of(classOf[VSConfiguration])
      def receive = {
        case d: DeadLetter ⇒ logger.debug("DeadLetter - sender: %s, recipient: %s, message: %s" format(d.sender.toString, d.recipient.toString, d.message.toString))
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }
  
  implicit val timeout: Timeout = {
    val r = """^(\d+)([^\d]+)$""".r
    val r(timeoutS, unitS) = configuration.getString("application.timeout") getOrElse sys.error("application.timeout")
    Timeout(Duration(timeoutS.toInt, unitS))
  }

  val storeDirectory = new File(configuration.getString("application.store.directory") getOrElse sys.error("application.store.directory"))

  lazy val store: RDFStore[Rdf, Future] =
    JenaStore(createDatasetGraph(storeDirectory.getAbsolutePath))

  // ouch :-)
//  http.authorityManagerFor("w3.org").sleepTime = 0
  
}
