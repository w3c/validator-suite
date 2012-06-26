package org.w3.vs

import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import akka.util.Timeout
import org.w3.banana._
import org.w3.vs.store.Binders

trait VSConfiguration {
  
  val system: ActorSystem
  
  val MAX_URL_TO_FETCH: Int
  
  val assertorExecutionContext: ExecutionContext
  
  val webExecutionContext: ExecutionContext

  val httpClient: AsyncHttpClient
  
  val timeout: Timeout

  type Rdf <: RDF
  type Sparql <: SPARQL
  type PointedGraph = org.w3.banana.PointedGraph[Rdf]

  val ops: RDFOperations[Rdf]

  val diesel: Diesel[Rdf]

  val store: AsyncRDFStore[Rdf, Sparql]

  val binders: Binders[Rdf]

  val SparqlOps: SPARQLOperations[Rdf, Sparql]

}
