package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
//import org.w3.vs.store._
import org.w3.vs.actor.JobsActor
import akka.util.Timeout
//import org.w3.banana._
//import org.w3.banana.jena._

trait VSConfiguration {
  
  val system: ActorSystem
  
  val MAX_URL_TO_FETCH: Int
  
  val assertorExecutionContext: ExecutionContext
  
  val webExecutionContext: ExecutionContext

  val storeExecutionContext: ExecutionContext
  
  val httpClient: AsyncHttpClient
  
  /*val store: Store2

  val rdfStore: RDFStore[Jena]

  val binders: Binders[Jena]

  val stores: Stores[Jena]*/

  val timeout: Timeout

}
