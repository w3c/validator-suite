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
  def byJobId(jobId: Job#Id): Option[JobLive]
  def byJobId(jobId: String): Option[JobLive]
  def runOf(job: Job): JobLive
}

class RunCreatorImpl()(implicit configuration: VSConfiguration, timeout: Timeout) extends RunCreator {
  
  var registry = Map[Job#Id, ActorRef]()
  
  def byJobId(jobId: Job#Id): Option[JobLive] = registry.get(jobId) map { new JobLive(_) }
  
  def byJobId(jobIdString: String): Option[JobLive] =
    for {
      jobId <- try Some(java.util.UUID.fromString(jobIdString)) catch { case t => None }
      jobLive <- byJobId(jobId)
    } yield jobLive
  
  def runOf(job: Job): JobLive = {
    val actor = TypedActor.context.actorOf(
      Props(new JobActor(job)), name = job.id.toString)
    registry += (job.id -> actor)
    new JobLive(actor)
  }

}

