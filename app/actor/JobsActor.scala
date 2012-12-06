package org.w3.vs.actor

import akka.actor._
import org.w3.vs.model._
import scalaz.Equal
import scalaz.Scalaz._
import org.w3.util.akkaext._
import org.w3.vs._
import diesel._
import org.w3.banana._
import org.w3.util.URL
import scala.concurrent._
import scala.util._

case class CreateJobAndForward(job: Job, initialState: JobActorState, run: Run, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall], msg: Any)

class JobsActor()(implicit conf: VSConfiguration) extends Actor with PathAwareActor {

  import conf._

  val logger = play.Logger.of(classOf[JobsActor])

  def userId: UserId = {
    val elements = self.path.elements.toList
    val userIdStr = elements(elements.size - 2)
    UserId(userIdStr)
  }

  def getJobRefOrCreate(job: Job, initialState: JobActorState, run: Run, toBeFetched: Iterable[URL], toBeAsserted: Iterable[AssertorCall]): ActorRef = {
    val id = job.id.toString
    try {
      context.actorOf(Props(new JobActor(job, initialState, run, toBeFetched, toBeAsserted)).withDispatcher("job-dispatcher"), name = id)
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
          import scala.concurrent.ExecutionContext.Implicits.global
          // should get the relPath and provide the uri to the job in the store
          // later: make the job actor as something backed by a graph in the store!
          val f: Future[CreateJobAndForward] =
            Job.get(JobId(id)) map { case (job, runIdOpt) =>
              runIdOpt match {
                case None => {
                  val run = Run.freshRun(userId, job.id, job.strategy)
                  CreateJobAndForward(job, NeverStarted, run, List.empty, List.empty, msg)
                }
                case Some((run, toBeFetched, toBeAsserted)) =>
                  CreateJobAndForward(job, Started, run, toBeFetched, toBeAsserted, msg)
              }
            }

          f onComplete {
            case Success(cjaf) => to.tell(cjaf, from)
            case Failure(exception) => logger.error("Couldn't find job with id: " + id + " ; " + msg, exception)
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

