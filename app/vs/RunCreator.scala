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
  def byJobId(jobId: JobConfiguration#Id): Option[Job]
  def byJobId(jobId: String): Option[Job]
  def runOf(job: JobConfiguration): Job
}

class RunCreatorImpl()(implicit configuration: VSConfiguration, timeout: Timeout) extends RunCreator {
  
  var registry = Map[JobConfiguration#Id, ActorRef]()
  
  def byJobId(jobId: JobConfiguration#Id): Option[Job] = registry.get(jobId) map { new Job(_) }
  
  def byJobId(jobIdString: String): Option[Job] =
    for {
      jobId <- try Some(java.util.UUID.fromString(jobIdString)) catch { case t => None }
      jobLive <- byJobId(jobId)
    } yield jobLive
  
  def runOf(job: JobConfiguration): Job = {
    val actor = TypedActor.context.actorOf(
      Props(new JobActor(job)), name = job.id.toString)
    registry += (job.id -> actor)
    new Job(actor)
  }

}

