package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, Props, ActorRef}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.run._
import org.w3.vs.model._
import org.w3.vs.assertor._

trait RunCreator {
  def byJobId(jobId: Job#Id): Option[ActorRef]
  def byJobId(jobId: String): Option[ActorRef]
  def runOf(job: Job): ActorRef
}
    
class RunCreatorImpl()(implicit val configuration: ValidatorSuiteConf) extends RunCreator {
  
  var registry = Map[Job#Id, ActorRef]()
  
  def byJobId(jobId: Job#Id): Option[ActorRef] = registry.get(jobId)
  
  def byJobId(jobIdString: String): Option[ActorRef] =
    try {
      val jobId: Job#Id = java.util.UUID.fromString(jobIdString)
      byJobId(jobId)
    } catch { case e =>
      None
    }
    
  def runOf(job: Job): ActorRef = {
    val obs = TypedActor.context.actorOf(
      Props(new Run(job)), name = job.id.toString)
    registry += (job.id -> obs)
    obs
  }
}

