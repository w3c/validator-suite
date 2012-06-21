package org.w3.vs

import akka.actor._
import org.w3.util._
import org.w3.vs.http._
import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.store._
import org.w3.vs.assertor._
import akka.util.duration._
import akka.util.Timeout
import akka.util.Duration
import akka.dispatch.ExecutionContext
import java.util.concurrent.Executors
import com.ning.http.client.{AsyncHttpClientConfig, AsyncHttpClient}
import org.w3.vs._
import org.w3.banana._
import org.w3.banana.jena._
import com.hp.hpl.jena.sparql.core._
import org.joda.time.DateTime
import org.joda.time._

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
        case d: DeadLetter ⇒ println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! DeadLetter: "+d)
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }
  
  val timeout: Timeout = 15.seconds

  type Rdf = Jena
  type Sparql = JenaSPARQL

  val ops: RDFOperations[Rdf] = JenaOperations

  val diesel: Diesel[Rdf] = JenaDiesel

  val store: AsyncRDFStore[Rdf, Sparql] = {
    val blockingStore = JenaStore(com.hp.hpl.jena.tdb.TDBFactory.createDatasetGraph())
    val asyncStore = AsyncRDFStore(blockingStore, system)(timeout)
    asyncStore
  }

  val binders: Binders[Rdf] = Binders(JenaDiesel)

  val SparqlOps: SPARQLOperations[Rdf, Sparql] = JenaSPARQLOperations
  
  // ouch :-)
//  http.authorityManagerFor("w3.org").sleepTime = 0
  
  
  // Don't know what the hell is wrong with this being in Global.scala, feel free to fix 
  implicit val c = this
  val orgId = OrganizationId()
  val tgambet = User(email = "tgambet@w3.org", name = "Thomas Gambet", password = "secret", organizationId = orgId)
  val bertails = User(email = "bertails@w3.org", name = "Alexandre Bertails", password = "secret", organizationId = orgId)
  val w3c = Organization(id = orgId, name = "World Wide Web Consortium", adminId = tgambet.id)
    
  val w3 = Job(
    createdOn = DateTime.now(DateTimeZone.UTC),
    name = "W3C",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.w3.org/"),
      linkCheck = false,
      maxResources = 2,
      filter = Filter(include = Everything, exclude = Nothing)))
      
  val tr = Job(
    createdOn = DateTime.now.plus(1000),
    name = "TR",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.w3.org/TR"),
      linkCheck = false,
      maxResources = 10,
      filter=Filter.includePrefixes("http://www.w3.org/TR")))
        
  val ibm = Job(
    createdOn = DateTime.now.plus(2000),
    name = "IBM",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.ibm.com"),
      linkCheck = false,
      maxResources = 20,
      filter = Filter(include=Everything, exclude=Nothing)))
    
  val lemonde = Job(
    createdOn = DateTime.now.plus(3000),
    name = "Le Monde",
    creatorId = bertails.id,
    organizationId = w3c.id,
    strategy = Strategy(
      entrypoint = URL("http://www.lemonde.fr"),
      linkCheck = false,
      maxResources = 30,
      filter = Filter(include = Everything, exclude = Nothing)))
  
  tgambet.save()
  bertails.save()
  w3c.save()
  w3.save()
  tr.save()
  ibm.save()
  lemonde.save()
  
}
