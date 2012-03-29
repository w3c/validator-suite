package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http.{ Http, HttpImpl }
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
import scalaz._
import Scalaz._
import akka.util.Timeout
import akka.util.duration._
import message._
import scalaz._

class JobsActor()(implicit configuration: VSConfiguration) extends Actor {

  def getOrCreateJob(jobId: JobId): ActorRef = {
    val name = jobId.toString
    context.children.find(_.path.name === name) getOrElse {
      configuration.store.getJobById(jobId) match {
        case Failure(t) => throw t
        case Success(None) => sys.error("couldn't find the configuration in store, this is bad")
        case Success(Some(jobConfiguration)) =>
          context.actorOf(Props(new JobActor(jobConfiguration)), name = name)
      }
    }
  }

  def receive = {
    case Message(_, jobId, msg) => {
      val jobRef = getOrCreateJob(jobId)
      jobRef.forward(msg)
    }
  }

}

