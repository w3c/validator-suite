package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, Props, ActorRef}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.observer._
import org.w3.vs.model._
import org.w3.vs.assertor._

trait ObserverCreator {
  def byJobId(jobId: Job#Id): Option[ActorRef]
  def byJobId(jobId: String): Option[ActorRef]
  def observerOf(job: Job): ActorRef
}
    
class ObserverCreatorImpl()(implicit val configuration: ValidatorSuiteConf) extends ObserverCreator {
  
  var registry = Map[Job#Id, ActorRef]()
  
  def byJobId(jobId: Job#Id): Option[ActorRef] = registry.get(jobId)
  
  def byJobId(jobIdString: String): Option[ActorRef] =
    try {
      val jobId: Job#Id = java.util.UUID.fromString(jobIdString)
      byJobId(jobId)
    } catch { case e =>
      None
    }
    
  def observerOf(job: Job): ActorRef = {
    val obs = TypedActor.context.actorOf(
      Props(new Observer(job)), name = job.id.toString)
    registry += (job.id -> obs)
    obs
  }
}

