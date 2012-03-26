package org.w3.vs.actor

import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import org.w3.vs.http.{ Http, HttpImpl }
import org.w3.vs.model._
import org.w3.vs.assertor._
import org.w3.vs.VSConfiguration
// import scalaz._
// import Validation._
import akka.util.Timeout
import akka.util.duration._

case class GetJobOrCreate(jobConfiguration: JobConfiguration)

class JobsActor()(implicit configuration: VSConfiguration) extends Actor {

  /* see https://groups.google.com/forum/#!msg/akka-user/uaFTSpLoGp0/8Fh18YN4lewJ */
  def receive = {
    case GetJobOrCreate(jobConfiguration) => {
      val name = jobConfiguration.id.toString
      def createChild =
        context.actorOf(Props(new JobActor(jobConfiguration)), name = name)
      val child = context.children.find(_.path.name == name).getOrElse(createChild)
      sender ! child
    }
  }

}

object JobsActor {

  def getJobOrCreate(
      jobConf: JobConfiguration)(
      implicit configuration: VSConfiguration): Future[Job] = {
    val context = configuration.system
    val jobsRef: ActorRef = context.actorFor("/user/jobs")
    implicit val timeout: Timeout = 5.seconds
    (jobsRef ? GetJobOrCreate(jobConf)).mapTo[ActorRef] map { jobRef => new Job(jobConf, jobRef) }
  }

}
