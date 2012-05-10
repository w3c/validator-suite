package org.w3.vs

import akka.actor._
import org.w3.vs.http._
import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Timeout
import akka.util.Duration
import akka.dispatch.ExecutionContext
import java.util.concurrent.Executors
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import org.w3.vs.store._
import org.w3.vs._
import org.w3.banana._
import org.w3.banana.jena._

trait DefaultProdConfiguration extends VSConfiguration {
  
  val MAX_URL_TO_FETCH = 10
  
  val assertorExecutionContext: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }
  
  val webExecutionContext: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(10)
    ExecutionContext.fromExecutorService(executor)
  }

  val storeExecutionContext: ExecutionContext = {
    import java.util.concurrent.{ExecutorService, Executors}
    val executor: ExecutorService = Executors.newFixedThreadPool(2)
    ExecutionContext.fromExecutorService(executor)
  }
    
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

  val system: ActorSystem = {
    val vs = ActorSystem("vs")
    vs.actorOf(Props(new OrganizationsActor()(this)), "organizations")
    vs.actorOf(Props(new Http()(this)), "http")
    val listener = vs.actorOf(Props(new Actor {
      def receive = {
        case d: DeadLetter ⇒ println("DeadLetter: "+d)
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }
  
  val store = new MemoryStore()(this)

  val rdfStore: RDFStore[Jena] =
    JenaStore(com.hp.hpl.jena.sparql.core.DatasetGraphFactory.createMem())

  val binders: Binders[Jena] =
    Binders(JenaOperations, JenaGraphUnion, JenaGraphTraversal)

  val stores: Stores[Jena] = Stores(rdfStore, binders)

  val timeout: Timeout = 10.seconds
  
  // ouch :-)
//  http.authorityManagerFor("w3.org").sleepTime = 0
  
}
