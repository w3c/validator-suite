package org.w3.vs.actor

import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs._
import akka.actor.{ActorSystem => AkkaActorSystem, _}

object RunsActor {

  private val logger = play.Logger.of(classOf[RunsActor])

  case class RunJob(job: Job)
  case class ResumeJob(job: Job)

  /** returns a String that can be used as an valid actor name
    * currently, actorOf's scaladoc says that it starts with a '$'
    * so we can't recreate it with the same name
    */
  private def createActorName(): String = java.util.UUID.randomUUID().toString

}

class RunsActor()(implicit conf: ValidatorSuite) extends Actor {

  import RunsActor.{ logger, createActorName, RunJob, ResumeJob }

  def receive = {

    case RunJob(job) => {
      val from = sender
      // start with a fresh job
      val run = Run.freshRun(job.strategy)
      // create the corresponding actor
      val jobActorRef = context.actorOf(Props(new JobActor(job, run)).withDispatcher("jobactor-dispatcher"), name = createActorName())
      jobActorRef.forward(JobActor.Start)
    }

    // this is supposed to be sent only when we restart the application
    // we assume that the job is in Running state
    case ResumeJob(job) => {
      val from = sender
      job.status match {
        // this is a valid message only if it was already Running
        case NeverStarted | Zombie => sys.error("not valid here")
        case Done(_, _, _, _) => sys.error("not valid here")
        case running @ Running(runId, actorName) => {
          // recover a coherent state from the database
          Run.get(runId) onComplete {
            case f @ Failure(t) => from ! f
            case Success((run, actions)) => {
              // re-create the corresponding actor
              // we force the actor name to avoid having to update the actorPath in the job record
              // it should be safe as we're starting from scratch
              val jobActorRef = context.actorOf(Props(new JobActor(job, run)).withDispatcher("jobactor-dispatcher"), name = actorName.name)
              // we can now tell the JobActor to resume its work
              jobActorRef.tell(JobActor.Resume(actions), from)
            }
          }
        }
      }
    }

    case a => {
      logger.error(s"runs-actor: unexpected ${a}")
    }

  }

}
