package org.w3.vs

import akka.actor._
import org.w3.vs.http._
import org.w3.vs.actor._
import akka.util.{ Duration, Timeout }
import akka.dispatch.ExecutionContext
import java.util.concurrent._
import com.ning.http.client.{ AsyncHttpClientConfig, AsyncHttpClient }
import org.w3.banana._
import org.w3.banana.jena._
import com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph
import com.typesafe.config.ConfigFactory

trait DefaultProdConfiguration extends VSConfiguration {

  val configuration = ConfigFactory.load()
  
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
    val httpClientConf = configuration.getConfig("application.http-client")
    val executor = Executors.newCachedThreadPool()
    val builder = new AsyncHttpClientConfig.Builder()
    val config =
      builder.setMaximumConnectionsTotal(httpClientConf.getInt("maximum-connections-total"))
      .setMaximumConnectionsPerHost(httpClientConf.getInt("maximum-connectionsper-host"))
      .setExecutorService(executor)
      .setFollowRedirects(false)
      .setConnectionTimeoutInMs(httpClientConf.getInt("timeout"))
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
  
  implicit val timeout: Timeout = Timeout(Duration(configuration.getString("application.timeout")))

  val storeDirectory = new java.io.File(configuration.getString("application.store.directory"))

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
