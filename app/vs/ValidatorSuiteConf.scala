package org.w3.vs

import akka.actor.ActorSystem
import org.w3.vs.http.Http
import org.w3.vs.assertor.AssertorPicker

trait ValidatorSuiteConf {
  
  val system: ActorSystem
  
  val http: Http

  val observerCreator: ObserverCreator
  
  val assertorPicker: AssertorPicker
  
}