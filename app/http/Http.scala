package org.w3.vs.http

import akka.actor._
import org.w3.vs.util._
import play.Logger
import org.w3.vs.model._
import com.ning.http.client._
import scalaz.Scalaz._
import org.w3.vs.util.akkaext._
import AuthorityManager.encode
import org.w3.vs.ValidatorSuite

object Http {

  def newAsyncHttpClient(strategy: Strategy)(implicit vs: ValidatorSuite): AsyncHttpClient = {
    val config = vs.config
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

  val logger = Logger.of(classOf[Http])

  case class SetSleepTime(value: Long)

  case object HowManyPendingRequests

}

import Http._

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class Http(httpClient: AsyncHttpClient, scheduler: Scheduler, cacheOpt: Option[Cache]) extends Actor with PathAwareActor {

  import Http.logger

  def getAuthorityManagerRefOrCreate(authority: Authority): ActorRef = {
    val encoded = encode(authority)
    try {
      context.children.find(_.path.name === encoded) getOrElse {
        context.actorOf(Props(new AuthorityManager(authority, httpClient, scheduler, cacheOpt)).withDispatcher("http-dispatcher"), name = encoded)
      }
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / encoded)
    }
  }

  def receive = {
    case Tell(Child(name), msg) => {
      val authorityManagerRef = getAuthorityManagerRefOrCreate(name)
      authorityManagerRef forward msg
    }
    case fetch @ Fetch(url, _) => {
      val authority = url.authority
      val authorityManagerRef = getAuthorityManagerRefOrCreate(authority)
      authorityManagerRef forward fetch
    }
  }
  
}

