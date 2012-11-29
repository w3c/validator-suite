package org.w3.vs

import akka.actor.ActorSystem
import scala.concurrent._
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.store.Binders
import org.w3.vs.http.Cache
import org.w3.banana._
import akka.util.Timeout

trait VSConfiguration {
  
  implicit val system: ActorSystem
  
  implicit val timeout: Timeout

  val httpCacheOpt: Option[Cache]

  val httpClient: AsyncHttpClient

  val store: RDFStore[Rdf, Future]

}
