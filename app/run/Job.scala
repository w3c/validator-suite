package org.w3.vs.run

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import akka.dispatch._
import akka.actor._
import play.Logger
import akka.util.duration._
import akka.util.Duration
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import akka.actor.Props
import java.nio.channels.ClosedChannelException

object Job {

  def getJobOrCreate(id: JobConfiguration#Id, job: => JobConfiguration)(implicit conf: VSConfiguration): Job = {
    import conf.jobCreator
    jobCreator.byJobId(id) getOrElse jobCreator.runOf(job)
  }

}

class Job(val jobActorRef: ActorRef)(implicit timeout: Timeout, conf: VSConfiguration) {

  import conf.system

  val logger = play.Logger.of(classOf[Job])
  
  def refresh(): Unit = jobActorRef ! message.Refresh
  
  def stop(): Unit = jobActorRef ! message.Stop

  def on(): Unit = jobActorRef ! message.BeProactive

  def off(): Unit = jobActorRef ! message.BeLazy

  def jobData(): Future[JobData] =
    (jobActorRef ? message.GetJobData).mapTo[JobData]

  def subscribeToUpdates(): Enumerator[message.RunUpdate] = {
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: message.RunUpdate =>
          try { 
            enumerator.push(msg)
          } catch { 
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
          	case e => enumerator.close; logger.error("Socket exception: ", e)
          }
        case msg => logger.debug("subscriber got "+msg)
      }
    }))
    // TODO make the enumerator to stop the actor and unsubscribe it when an error occurs (or when it's 
    lazy val enumerator: PushEnumerator[message.RunUpdate] =
      Enumerator.imperative[message.RunUpdate](
        onStart = jobActorRef.tell(message.Subscribe, subscriber),
        onComplete = jobActorRef.tell(message.Unsubscribe, subscriber),
        onError = (_,_) => jobActorRef.tell(message.Unsubscribe, subscriber)
      )
    enumerator
  }
  
}
