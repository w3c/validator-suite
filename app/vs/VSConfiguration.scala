package org.w3.vs

import akka.actor.ActorSystem
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import akka.util.Timeout
import org.w3.vs.store.Binders
import org.w3.banana._

trait VSConfiguration {
  
  val system: ActorSystem
  
  val assertorExecutionContext: ExecutionContext
  
  val webExecutionContext: ExecutionContext

  val httpClient: AsyncHttpClient
  
  implicit val timeout: Timeout

  val store: AsyncRDFStore[Rdf]

}
