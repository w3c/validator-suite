package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http._
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import akka.util.Timeout
import akka.util.duration._
import message._
import scalaz._
import org.w3.util.akkaext._

case class CreateJobAndForward(jobConfiguration: JobConfiguration, msg: Any)

class JobsActor()(implicit configuration: VSConfiguration) extends Actor with PathAwareActor {

  val logger = play.Logger.of(classOf[JobsActor])

  def getJobRefOrCreate(jobConfiguration: JobConfiguration): ActorRef = {
    val id = jobConfiguration.id
    val name = id.toString
    try {
      context.actorOf(Props(new JobActor(jobConfiguration)), name = name)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / name)
    }
  }

  def receive = {

    case Tell(Child(name), msg) => {
      val from = sender
      val to = self
      context.children.find(_.path.name === name) match {
        case Some(jobRef) => jobRef forward msg
        case None => {
          val jobId = JobId.fromString(name)
          configuration.store.getJobById(jobId).asFuture onSuccess {
            case Success(jobConfiguration) => {
              to.tell(CreateJobAndForward(jobConfiguration, msg), from)
            }
            case Failure(storeException) => {
              logger.error(storeException.toString)
            }
          }
        }
      }
    }

    case CreateJobAndForward(jobConfiguration, msg) => {
      val jobRef = getJobRefOrCreate(jobConfiguration)
      jobRef forward msg
    }

  }

}

