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

case class CreateJobAndForward(jobConfiguration: JobConfiguration, msg: Message)

class JobsActor()(implicit configuration: VSConfiguration) extends Actor {

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

    case m @ Message(_, jobId, msg) => {
      val name = jobId.toString
      val from = sender
      val to = self
      context.children.find(_.path.name === name) match {
        case Some(jobRef) => jobRef forward msg
        case None => {
          configuration.store.getJobById(jobId).asFuture onSuccess {
            case Success(jobConfiguration) => {
              to.tell(CreateJobAndForward(jobConfiguration, m), from)
            }
            case Failure(storeException) => {
              sys.error(storeException.toString)
            }
          }
        }
      }
    }

    case CreateJobAndForward(jobConfiguration, Message(_, _, msg)) => {
      val jobRef = getJobRefOrCreate(jobConfiguration)
      jobRef forward msg
    }

  }

}

