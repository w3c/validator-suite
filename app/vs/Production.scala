package org.w3.vs

import akka.actor.ActorSystem
import akka.actor.TypedActor
import org.w3.vs.http.{Http, HttpImpl}
import akka.actor.Props
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Duration
import akka.dispatch.ExecutionContext
import java.util.concurrent.Executors
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.AsyncHttpClient

trait Production extends ValidatorSuiteConf {
  
  val MAX_URL_TO_FETCH = 10
  
  val validatorDispatcher: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }
  
  val system: ActorSystem = ActorSystem("vs")
  
  lazy val http: Http =
    TypedActor(system).typedActorOf(
      classOf[Http],
      new HttpImpl()(this),
      Props(),
      "http")
  
  lazy val observerCreator: ObserverCreator =
    TypedActor(system).typedActorOf(
      classOf[ObserverCreator],
      new ObserverCreatorImpl()(this),
      Props(),
      "observer")
  
  /**
   * note: an AsyncHttpClient is a heavy object with a thread
   * and connection pool associated with it, it's supposed to
   * be shared among lots of requests, not per-http-request
   */
  lazy val httpClient = {
    // 2 seconds
    val timeout: Int = 2000
    val executor = Executors.newCachedThreadPool()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder.setMaximumConnectionsTotal(1000)
      .setMaximumConnectionsPerHost(15)
      .setExecutorService(executor)
      .setFollowRedirects(true)
      .setConnectionTimeoutInMs(timeout)
      .build
    new AsyncHttpClient(config)
  }

  /**
   * use this to make sure that all lazy services are instantiated during startup
   */
  def init(): Unit = {
    httpClient
    observerCreator
      // ouch :-)
    http.authorityManagerFor("w3.org").sleepTime = 0
  }

  init()
  
}
