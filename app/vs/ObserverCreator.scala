package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, Props, ActorRef}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._

trait ObserverCreator {
  def byRunId(runId: Run#Id): Option[ActorRef]
  def byRunId(runId: String): Option[ActorRef]
  def observerOf(run: Run): ActorRef
}
    
class ObserverCreatorImpl()(implicit val configuration: ValidatorSuiteConf) extends ObserverCreator {
  
  var registry = Map[Run#Id, ActorRef]()
  
  def byRunId(runId: Run#Id): Option[ActorRef] = registry.get(runId)
  
  def byRunId(runIdString: String): Option[ActorRef] =
    try {
      val runId: Run#Id = java.util.UUID.fromString(runIdString)
      byRunId(runId)
    } catch { case e =>
      None
    }
    
  def observerOf(run: Run): ActorRef = {
    val obs = TypedActor.context.actorOf(
      Props(new Observer(run)), name = run.id.toString)
    registry += (run.id -> obs)
    obs
  }
}

