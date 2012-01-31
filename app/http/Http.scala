package org.w3.vs.http

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import org.w3.util._
import akka.util.Duration
import akka.util.duration._
import play.Logger
import org.w3.vs.observer.Observer
import org.w3.vs.model.{FetchAction, FetchHEAD, FetchGET}

trait Http {
  def fetch(url: URL, action: FetchAction, observer: Observer): Unit
  def authorityManagerFor(url: URL): AuthorityManager
  def authorityManagerFor(authority: Authority): AuthorityManager
}

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class HttpImpl extends Http with TypedActor.PostStop {

  // TODO really???
  import TypedActor.dispatcher
  
  val logger = Logger.of(classOf[Http])
  
  val asyncHttpClient = Http.makeClient(2 seconds)
  
  var registry = Map[Authority, AuthorityManager]()
  
  def authorityManagerFor(url: URL): AuthorityManager =
    authorityManagerFor(url.authority)
  
  def authorityManagerFor(authority: Authority): AuthorityManager = {
    registry.get(authority).getOrElse {
      val authorityManager = TypedActor(TypedActor.context).typedActorOf(
        classOf[AuthorityManager],
        new AuthorityManagerImpl(authority, asyncHttpClient),
        Props(),
        authority)
      registry += (authority -> authorityManager)
      authorityManager
    }
  }
  
  def fetch(url: URL, action: FetchAction, observer: Observer): Unit =
    authorityManagerFor(url).fetch(url, action, observer)
  
  override def postStop = {
    logger.debug("closing asyncHttpClient")
    asyncHttpClient.close()
  }
  
}

object Http {
  
  // This field is just used for debug/logging/testing
  val httpInFlight = new AtomicInteger(0)

  // note: an AsyncHttpClient is a heavy object with a thread
  // and connection pool associated with it, it's supposed to
  // be shared among lots of requests, not per-http-request
  private[http] def makeClient(timeout: Duration)(implicit dispatcher: MessageDispatcher) = {
    val executor = Executors.newCachedThreadPool()
    
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder.setMaximumConnectionsTotal(1000)
      .setMaximumConnectionsPerHost(15)
      .setExecutorService(executor)
      .setFollowRedirects(true)
      .setConnectionTimeoutInMs(timeout.toMillis.toInt)
      .build
    new AsyncHttpClient(config)
  }
  
}
