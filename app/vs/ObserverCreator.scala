package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, TypedProps}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._

trait ObserverCreator {
  def byRunId(runId: Run#Id): Option[Observer]
  def byRunId(runId: String): Option[Observer]
  def observerOf(run: Run): Observer
}
    
class ObserverCreatorImpl()(implicit val configuration: ValidatorSuiteConf) extends ObserverCreator {
  
  var registry = Map[Run#Id, Observer]()
  
  def byRunId(runId: Run#Id): Option[Observer] = registry.get(runId)
  
  def byRunId(runIdString: String): Option[Observer] =
    try {
      val runId: Run#Id = java.util.UUID.fromString(runIdString)
      byRunId(runId)
    } catch { case e =>
      None
    }
    
  def observerOf(run: Run): Observer = {
    val obs = TypedActor(TypedActor.context).typedActorOf(
      TypedProps(
        classOf[Observer],
        new ObserverImpl(run)),
      run.id.toString)
    registry += (run.id -> obs)
    obs
  }
}

