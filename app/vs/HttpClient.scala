package org.w3.vs

import org.w3.vs.http.{Http, Cache}
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig}
import akka.actor.{Props, ActorRef}

trait HttpClient extends ValidatorSuite {
  this: ValidatorSuite =>

  def httpCacheOpt: Option[Cache]

  def httpClient: AsyncHttpClient

}

trait DefaultHttpClient extends HttpClient {
  this: ValidatorSuite
    with ActorSystem =>

  /**
   * note: an AsyncHttpClient is a heavy object with a thread
   * and connection pool associated with it, it's supposed to
   * be shared among lots of requests, not per-http-request
   */
  val httpClient = {
    // in future version of Typesafe's Config: s/getConfig/atPath/
    val httpClientConf = config.getConfig("application.http-client") getOrElse sys.error("application.http-client")
    //    val executor = new ForkJoinPool()
    val maxConnectionsTotal = httpClientConf.getInt("maximum-connections-total") getOrElse sys.error("maximum-connections-total")
    val maxConnectionsPerHost = httpClientConf.getInt("maximum-connectionsper-host") getOrElse sys.error("maximum-connectionsper-host")
    val timeout = httpClientConf.getInt("timeout") getOrElse sys.error("timeout")
    val builder = new AsyncHttpClientConfig.Builder()
    val asyncHttpConfig =
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
    new AsyncHttpClient(asyncHttpConfig)
  }

  val httpCacheOpt: Option[Cache] = Cache(config)

  override def shutdown() {
    logger.info("Closing HTTPClient")
    httpClient.close()
    super.shutdown()
  }

}
