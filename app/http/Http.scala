package org.w3.vs.http

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import akka.event.EventHandler
import org.w3.util._
import akka.util.Duration
import akka.util.duration._
import play.Logger

trait Http {
  def GET(url: URL, distance: Int, actionManagerId: String): Unit
  def HEAD(url: URL, actionManagerId: String): Unit
  def authorityManagerFor(url: URL): AuthorityManager
}

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
private[http] class HttpImpl extends TypedActor with Http {

  val logger = Logger.of(classOf[Http])
  
  val asyncHttpClient = Http.makeClient(2 seconds)

  val authorityManagerDispatcher =
    Dispatchers.newExecutorBasedEventDrivenDispatcher("authority-manager")
            .withNewThreadPoolWithLinkedBlockingQueueWithCapacity(100)
            .setCorePoolSize(8)
            .setMaxPoolSize(8)
            .setKeepAliveTime(4 seconds)
            .setRejectionPolicy(new CallerRunsPolicy)
            .build
    
  val configuration =
    TypedActorConfiguration(3000).dispatcher(authorityManagerDispatcher)
    
  def authorityManagerFor(url: URL): AuthorityManager = {
    val authority = url.authority
    Actor.registry.typedActorsFor(authority).headOption match {
      case Some(authorityManager) => authorityManager.asInstanceOf[AuthorityManager]
      case None => {
        val authorityManager =
          TypedActor.newInstance(
            classOf[AuthorityManager],
            new AuthorityManagerImpl(authority, asyncHttpClient),
            configuration)
        TypedActor.link(context.getSelf, authorityManager)
        authorityManager
      }
    }
  }
  
  def GET(url: URL, distance: Int, actionManagerId: String): Unit =
    authorityManagerFor(url).GET(url, distance, actionManagerId)
  
  def HEAD(url: URL, actionManagerId: String): Unit =
    authorityManagerFor(url).HEAD(url, actionManagerId)
  
  override def postStop = {
    val managers =
      Actor.registry.typedActorsFor[AuthorityManager]
    logger.debug(managers.size + " AuthorityManagers to be stopped")
    managers foreach { TypedActor.stop(_) }
    logger.debug("closing asyncHttpClient")
    asyncHttpClient.close()
  }
    
}

object Http {
  
  def getInstance(): Http =
    TypedActor.newInstance(
      classOf[Http],
      new HttpImpl,
      2.seconds.toMillis)
  
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
    
  private[http] def GET(asyncHttpClient: AsyncHttpClient, u: URL): Future[GETResponse] = 
    fetchURL(
      asyncHttpClient,
      asyncHttpClient.prepareGet _,
      u,
      GETResponse.apply _)
    
  private[http] def HEAD(asyncHttpClient: AsyncHttpClient, u: URL): Future[HEADResponse] = 
    fetchURL(
      asyncHttpClient,
      asyncHttpClient.prepareHead _,
      u,
      { (status: Int, headers: Headers, _) => HEADResponse(status, headers) }
    )
      
  private[http] def fetchURL[T <: HttpOutgoing](
    asyncHttpClient: AsyncHttpClient,
    prepare: String => AsyncHttpClient#BoundRequestBuilder,
    u: URL,
    f: (Int, Headers, String) => T): Future[T] = {
      
      // timeout the Akka future 50ms after we'd have timed out the request anyhow,
      // gives us 50ms to parse the response
      val future =
        new DefaultCompletableFuture[T](
          asyncHttpClient.getConfig().getRequestTimeoutInMs() + 50,
          TimeUnit.MILLISECONDS)
      
      val httpHandler: AsyncHandler[Unit] = new AsyncHandler[Unit]() {
        
        httpInFlight.incrementAndGet()
        
        val builder =
          new Response.ResponseBuilder()
        
        var finished = false
        
        // We can have onThrowable called because onCompleted
        // throws, and other complex situations, so to handle everything
        // we use this
        private def finish(body: => Unit): Unit = {
          if (!finished) {
            try {
              body
            } catch {
              case t: Throwable => {
                EventHandler.debug(this, t.getMessage)
                future.completeWithException(t)
                throw t // rethrow for benefit of AsyncHttpClient
              }
            } finally {
              finished = true
              httpInFlight.decrementAndGet()
              assert(future.isCompleted)
            }
          }
        }

        // this can be called if any of our other methods throws,
        // including onCompleted.
        def onThrowable(t: Throwable) {
          finish { throw t }
        }

        def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
          builder.accumulate(bodyPart)
          
          AsyncHandler.STATE.CONTINUE
        }
        
        def onStatusReceived(responseStatus: HttpResponseStatus) = {
          builder.accumulate(responseStatus)
          
          AsyncHandler.STATE.CONTINUE
        }
        
        def onHeadersReceived(responseHeaders: HttpResponseHeaders) = {
          builder.accumulate(responseHeaders)
          
          AsyncHandler.STATE.CONTINUE
        }
        
        def onCompleted() = {
          import scala.collection.JavaConverters._
          
          finish {
            val response = builder.build()
            
            import java.util.{Map => jMap, List => jList}
            import scala.collection.JavaConverters._
            
            val headers: Headers =
              (response.getHeaders().asInstanceOf[jMap[String, jList[String]]].asScala mapValues { _.asScala.toList }).toMap
            
            val body = response.getResponseBody()
            
            future.completeWithResult(f(response.getStatusCode(), headers, body))
          }
        }
      }
      
      prepare(u.toExternalForm()).execute(httpHandler)
      future
    }

}
