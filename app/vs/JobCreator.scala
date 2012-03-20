package org.w3.vs

import akka.actor.{ActorSystem, TypedActor, Props, ActorRef}
import org.w3.vs.http.{Http, HttpImpl}
import org.w3.vs.run._
import org.w3.vs.model._
import org.w3.vs.assertor._
import scalaz._
import Validation._
import akka.util.Timeout

trait JobCreator {
  def byJobId(jobId: JobId): Option[Job]
  //def byJobId(jobId: String): Option[Job]
  def runOf(job: JobConfiguration): Job
}

class JobCreatorImpl()(implicit configuration: VSConfiguration, timeout: Timeout) extends JobCreator {
  
  var registry = Map[JobId, (JobConfiguration, ActorRef)]()
  
  def byJobId(jobId: JobId): Option[Job] =
    registry.get(jobId) map { case (conf, ref) => new Job(conf, ref) }
  
  def runOf(jobConf: JobConfiguration): Job = {
    val actorRef = TypedActor.context.actorOf(
      Props(new JobActor(jobConf)), name = jobConf.id.toString)
    registry += (jobConf.id -> (jobConf, actorRef))
    new Job(jobConf, actorRef)
  }

}

