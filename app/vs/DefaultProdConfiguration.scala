package org.w3.vs

import akka.actor._
import org.w3.util._
import org.w3.vs.http._
import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.store._
import akka.util.duration._
import akka.util.Timeout
import akka.dispatch.ExecutionContext
import java.util.concurrent._
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import org.joda.time._
import org.w3.banana._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph

trait DefaultProdConfiguration extends VSConfiguration {
  
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
    // 2 seconds
    val timeout: Int = 2000
    val executor = Executors.newCachedThreadPool()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder.setMaximumConnectionsTotal(1000)
      .setMaximumConnectionsPerHost(15)
      .setExecutorService(executor)
      .setFollowRedirects(false)
      .setConnectionTimeoutInMs(timeout)
      .build
    new AsyncHttpClient(config)
  }

  implicit val system: ActorSystem = {
    val vs = ActorSystem("vs")
    vs.actorOf(Props(new OrganizationsActor()(this)), "organizations")
    vs.actorOf(Props(new Http()(this)), "http")
    val listener = vs.actorOf(Props(new Actor {
      val logger = play.Logger.of(classOf[VSConfiguration])
      def receive = {
        case d: DeadLetter ⇒ logger.debug("DeadLetter - sender: %s, recipient: %s, message: %s" format(d.sender.toString, d.recipient.toString, d.message.toString))
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }
  
  implicit val timeout: Timeout = 15.seconds

  val storeDirectory = new java.io.File("./data")

  lazy val store: RDFStore[Rdf, BananaFuture] =
    JenaStore(createDatasetGraph(storeDirectory.getAbsolutePath))

//  val store: AsyncRDFStore[Rdf] = {
//    import org.openrdf.sail.memory.MemoryStore
//    import org.openrdf.sail.nativerdf.NativeStore
//    import org.openrdf.repository.sail.SailRepository
//    val blockingStore = SesameStore {
//      val nativeStore = new NativeStore(new java.io.File("/tmp/foo"), "spoc,posc")
//      val repo = new SailRepository(nativeStore)
//      repo.initialize()
//      repo
//    }
//    val asyncStore = AsyncRDFStore(blockingStore, system)(timeout)
//    asyncStore
//  }

  // ouch :-)
//  http.authorityManagerFor("w3.org").sleepTime = 0
  
}
