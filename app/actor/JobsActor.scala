package org.w3.vs.actor

import akka.actor._
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import scalaz._
import org.w3.util.akkaext._

case class CreateJobAndForward(job: Job, msg: Any)

class JobsActor()(implicit configuration: VSConfiguration) extends Actor with PathAwareActor {

  val logger = play.Logger.of(classOf[JobsActor])

  def orgId: OrganizationId = {
    val elements = self.path.elements.toList
    val orgIdStr = elements(elements.size - 2)
    OrganizationId(orgIdStr)
  }

  def getJobRefOrCreate(job: Job): ActorRef = {
    val id = job.id.toString
    try {
      context.actorOf(Props(new JobActor(job)), name = id)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / id)
    }
  }

  def receive = {

    case Tell(Child(id), msg) => {
      val from = sender
      val to = self
      context.children.find(_.path.name === id) match {
        case Some(jobRef) => jobRef forward msg
        case None => {
          // should get the relPath and provide the uri to the job in the store
          // later: make the job actor as something backed by a graph in the store!
          Job.get(orgId, JobId(id)).onComplete {
            case Success(job) => to.tell(CreateJobAndForward(job, msg), from)
            case Failure(exception) => logger.error("Couldn't find job with id: " + id + " ; " + msg, exception)
          }
        }
      }
    }

    case CreateJobAndForward(job, msg) => {
      val jobRef = getJobRefOrCreate(job)
      jobRef forward msg
    }
    
    case a => {logger.error("unexpected message: " + a.toString)}

  }

}

