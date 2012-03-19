package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, TypedProps}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.run._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Duration
import akka.dispatch.ExecutionContext
import java.util.concurrent.Executors
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import org.w3.vs.store._
import org.w3.vs._

trait DefaultProdConfiguration extends VSConfiguration {
  
  val MAX_URL_TO_FETCH = 10
  
  lazy val assertorExecutionContext: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }
  
  lazy val webExecutionContext: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }
  
  val system: ActorSystem = ActorSystem("vs")
  
  val http: Http =
    TypedActor(system).typedActorOf(
      TypedProps(
        classOf[Http],
        new HttpImpl()(this)),
      "http")
    
  
  val jobCreator: JobCreator =
    TypedActor(system).typedActorOf(
      TypedProps(
        classOf[JobCreator],
        new JobCreatorImpl()(this, 5 seconds)),
      "run")
  
  /**
   * note: an AsyncHttpClient is a heavy object with a thread
   * and connection pool associated with it, it's supposed to
   * be shared among lots of requests, not per-http-request
   */
  val httpClient = {
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
  
  val store = new MemoryStore
  
  // ouch :-)
  http.authorityManagerFor("w3.org").sleepTime = 0
  
}
