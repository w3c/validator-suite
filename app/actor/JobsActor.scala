package org.w3.vs.actor

import akka.actor._
import org.w3.vs.model._
import scalaz._
import scalaz.Scalaz._
import org.w3.util.akkaext._
import org.w3.vs._
import diesel._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.util.URL

case class CreateJobAndForward(job: Job, initialState: JobActorState, run: Run, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall], msg: Any)

class JobsActor()(implicit conf: VSConfiguration) extends Actor with PathAwareActor {

  import conf._

  val logger = play.Logger.of(classOf[JobsActor])

  def orgId: OrganizationId = {
    val elements = self.path.elements.toList
    val orgIdStr = elements(elements.size - 2)
    OrganizationId(orgIdStr)
  }

  def getJobRefOrCreate(job: Job, initialState: JobActorState, run: Run, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall]): ActorRef = {
    val id = job.id.toString
    try {
      context.actorOf(Props(new JobActor(job, initialState, run, toBeFetched, toBeAsserted)), name = id)
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
          val f: BananaFuture[CreateJobAndForward] =
            Job.bananaGet(orgId, JobId(id)) flatMap { case (job, runUriOpt) =>
              runUriOpt match {
                case None => {
                  val (run, urls) = Run.freshRun(orgId, job.id, job.strategy)
                  val noCall: Iterable[AssertorCall] = Set.empty[AssertorCall]
                  CreateJobAndForward(job, NeverStarted, run, urls, noCall, msg).bf
                }
                case Some(runUri) =>
                  Run.bananaGet(runUri) map { case (run, toBeFetched, toBeAsserted) =>
                    CreateJobAndForward(job, Started, run, toBeFetched, toBeAsserted, msg)
                  }
              }
            }

          f.inner onComplete {
            case Right(Success(cjaf)) => to.tell(cjaf, from)
            case Right(Failure(exception)) => logger.error("Couldn't find job with id: " + id + " ; " + msg, exception)
            case Left(exception) => logger.error("Couldn't find job with id: " + id + " ; " + msg, exception)
          }
        }
      }
    }

    case CreateJobAndForward(job, initialState, run, toBeFetched, toBeCalled, msg) => {
      val jobRef = getJobRefOrCreate(job, initialState, run, toBeFetched, toBeCalled)
      jobRef forward msg
    }
    
    case a => logger.error("unexpected message: " + a.toString)

  }

}

