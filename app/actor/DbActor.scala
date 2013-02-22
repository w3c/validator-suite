package org.w3.vs.actor

import akka.actor._
import akka.pattern.ask
import akka.event._
import akka.util.Timeout
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._
import scala.concurrent._
import scala.concurrent.duration.Duration

class DbActor()(implicit conf: VSConfiguration) extends Actor {

  import scala.concurrent.ExecutionContext.Implicits.global

  def receive = {
    case event@CreateRunEvent(userId, jobId, runId, actorPath, strategy, createdAt, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val running = Running(runId, actorPath)
        Job.updateStatus(jobId, status = running)
      }
    }
    case event@CompleteRunEvent(userId, jobId, runId, runData, resourceDatas, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val done = Done(runId, Completed, timestamp, runData)
        Job.updateStatus(jobId, status = done, latestDone = done)
      }
    }
    case event@CancelRunEvent(userId, jobId, runId, runData, resourceDatas, timestamp) => {
      Run.saveEvent(event) flatMap { _ =>
        val done = Done(runId, Cancelled, timestamp, runData)
        Job.updateStatus(jobId, status = done, latestDone = done)
      }
    }
    case event@AssertorResponseEvent(userId, jobId, runId, ar, timestamp) => {
      Run.saveEvent(event)
    }
    case event@ResourceResponseEvent(userId, jobId, runId, rr, timestamp) => {
      Run.saveEvent(event)
    }
  }

}
