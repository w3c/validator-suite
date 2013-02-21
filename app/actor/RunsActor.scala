package org.w3.vs.actor

import akka.actor._
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._
import scala.concurrent.ExecutionContext.Implicits.global

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

class RunsActor()(implicit conf: VSConfiguration) extends Actor {

  import RunsActor.{ logger, createActorName, RunJob, ResumeJob }

  def receive = {

    case RunJob(job) => {
      val from = sender
      // start with a fresh job
      val run = Run.freshRun(job.strategy)
      // create the corresponding actor
      val jobActorRef = context.actorOf(Props(new JobActor(job, run)), name = createActorName())
      val running = Running(run.runId, jobActorRef.path)
      val createRunEvent = CreateRunEvent(run.runId, job.strategy, job.createdOn)

      // save the first RunEvent
      // then update the job status
      // then tells the sender
      val actions = job.status match {
        case NeverStarted | Zombie => {
          Run.saveEvent(createRunEvent) flatMap { _ =>
            Job.updateStatus(job.id, status = running)(conf)
          }
        }
        case running @ Running(_, actorPath) => {
          // in this case, we also need to stop the current JobActor supporting this run
          val actorRef = conf.system.actorFor(actorPath)
          context.stop(actorRef) // <- this is non-blocking at all
          Run.saveEvent(createRunEvent) flatMap { _ =>
            Job.updateStatus(job.id, status = running)(conf)
          }
        }
        case done @ Done(_, _, _, _) => {
          Run.saveEvent(createRunEvent) flatMap { _ =>
            // let's not forget to udpate the latestDone
            Job.updateStatus(job.id, status = running, latestDone = done)(conf)
          }
        }
      }

      // when the actions are done, we send the new JobStatus to the sender
      // and we tell the JobActor to start the run
      actions onComplete {
        case f @ Failure(t) => from ! f
        case Success(_) => {
          jobActorRef ! JobActor.Start
          from ! running
        }
      }

    }

    // this is supposed to be sent only when we restart the application
    // we assume that the job is in Running state
    case ResumeJob(job) => {
      val from = sender
      job.status match {
        // this is a valid message only if it was already Running
        case NeverStarted | Zombie => sys.error("not valid here")
        case Done(_, _, _, _) => sys.error("not valid here")
        case running @ Running(runId, actorPath) => {
          // recover a coherent state from the database
          Run.get(runId) onComplete {
            case f @ Failure(t) => from ! f
            case Success((run, toBeFetched, toBeAsserted)) => {
              // re-create the corresponding actor
              // we force the actor name to avoid having to update the actorPath in the job record
              // it should be safe as we're starting from scratch
              val jobActorRef = context.actorOf(Props(new JobActor(job, run)), name = actorPath.name)
              // we can now tell the JobActor to resume its work
              jobActorRef ! JobActor.Resume(toBeFetched, toBeAsserted)
              from ! ()
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
