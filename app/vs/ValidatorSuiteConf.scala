package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import akka.dispatch.ExecutionContext
import com.ning.http.client.AsyncHttpClient
import org.w3.vs.store.Store

trait ValidatorSuiteConf {
  
  val system: ActorSystem
  
  val http: Http

  val runCreator: RunCreator
  
  val MAX_URL_TO_FETCH: Int
  
  val validatorDispatcher: ExecutionContext
  
  val httpClient: AsyncHttpClient
  
  val store: Store
  
}