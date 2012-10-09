package org.w3.vs

import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import akka.util.Timeout
import org.w3.vs.store.Binders
import org.w3.banana._

trait VSConfiguration {
  
  implicit val system: ActorSystem
  
  implicit val timeout: Timeout

  val assertorExecutionContext: ExecutionContext

  val httpClient: AsyncHttpClient

  val store: RDFStore[Rdf, BananaFuture]

}
