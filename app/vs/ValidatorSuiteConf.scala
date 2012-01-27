package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import org.w3.vs.assertor.AssertorPicker
import akka.dispatch.ExecutionContext

trait ValidatorSuiteConf {
  
  val system: ActorSystem
  
  val http: Http

  val observerCreator: ObserverCreator
  
  val assertorPicker: AssertorPicker
  
  val MAX_URL_TO_FETCH: Int
  
  val validatorDispatcher: ExecutionContext
  
}