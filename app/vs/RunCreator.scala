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
  def byJobId(jobId: Job#Id): Validation[Throwable, Run]
  def byJobId(jobId: String): Validation[Throwable, Run]
  def runOf(job: Job): Run
}

class RunCreatorImpl()(implicit configuration: ValidatorSuiteConf, timeout: Timeout) extends RunCreator {
  
  var registry = Map[Job#Id, ActorRef]()
  
  def byJobId(jobId: Job#Id): Validation[Throwable, Run] = fromTryCatch { new Run(registry(jobId)) }
  
  def byJobId(jobIdString: String): Validation[Throwable, Run] =
    for {
      jobId <- fromTryCatch { java.util.UUID.fromString(jobIdString) }
      run <- byJobId(jobId)
    } yield run
  
  def runOf(job: Job): Run = {
    val actor = TypedActor.context.actorOf(
      Props(new RunActor(job)), name = job.id.toString)
    registry += (job.id -> actor)
    new Run(actor)
  }
}

