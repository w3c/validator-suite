package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.store.Store
import org.w3.vs.actor.Jobs

trait VSConfiguration {
  
  val system: ActorSystem
  
  val http: Http

  val jobs: Jobs
  
  val MAX_URL_TO_FETCH: Int
  
  val assertorExecutionContext: ExecutionContext
  
  val webExecutionContext: ExecutionContext
  
  val httpClient: AsyncHttpClient
  
  val store: Store
  
}
