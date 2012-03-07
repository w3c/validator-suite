package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, Props, ActorRef}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.run._
import org.w3.vs.model._
import org.w3.vs.assertor._
import scalaz._
import Validation._
import akka.util.Timeout

trait RunCreator {
  def byJobId(jobId: Job#Id): Option[Run]
  def byJobId(jobId: String): Option[Run]
  def runOf(job: Job): Run
}

class RunCreatorImpl()(implicit configuration: VSConfiguration, timeout: Timeout) extends RunCreator {
  
  var registry = Map[Job#Id, ActorRef]()
  
  def byJobId(jobId: Job#Id): Option[Run] = registry.get(jobId) map { new Run(_) }
  
  def byJobId(jobIdString: String): Option[Run] =
    for {
      jobId <- try Some(java.util.UUID.fromString(jobIdString)) catch { case t => None }
      job <- byJobId(jobId)
    } yield job
  
  def runOf(job: Job): Run = {
    val actor = TypedActor.context.actorOf(
      Props(new RunActor(job)), name = job.id.toString)
    registry += (job.id -> actor)
    new Run(actor)
  }
}

