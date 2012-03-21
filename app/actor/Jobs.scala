package org.w3.vs.actor

import akka.actor.{ ActorSystem, TypedActor, Props, ActorRef }
import org.w3.vs.http.{ Http, HttpImpl }
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Validation._
import akka.util.Timeout

trait Jobs {

  def byJobId(jobId: JobId): Option[Job]

  def runOf(job: JobConfiguration): Job

}

class JobsImpl()(implicit configuration: VSConfiguration, timeout: Timeout) extends Jobs {

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

