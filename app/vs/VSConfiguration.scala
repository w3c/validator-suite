package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.actor.JobsActor
import akka.util.Timeout
import org.w3.banana._
import org.w3.banana.jena._
import org.w3.vs.model.Binders

trait VSConfiguration {
  
  val system: ActorSystem
  
  val MAX_URL_TO_FETCH: Int
  
  val assertorExecutionContext: ExecutionContext
  
  val webExecutionContext: ExecutionContext

  val httpClient: AsyncHttpClient
  
  type Rdf <: RDF
  type Sparql <: SPARQL

  val store: AsyncRDFStore[Rdf, Sparql]

  val binders: Binders[Rdf]

  val timeout: Timeout

}
