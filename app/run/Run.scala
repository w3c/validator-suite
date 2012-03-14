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

class Run(val actorRef: ActorRef)(implicit timeout: Timeout) {
  
  val logger = play.Logger.of(classOf[Run])
  
  def start(): Unit = actorRef ! message.Start
  
  // TODO
  def stop(): Unit = {}
  
  def jobData(): Future[JobData] =
    (actorRef ? message.GetJobData).mapTo[JobData]
  
  def status(): Future[RunStatus] =
    (actorRef ? message.GetStatus).mapTo[RunStatus]
  
  def subscribeToUpdates()(implicit conf: VSConfiguration): Enumerator[message.RunUpdate] = {
    import conf.system
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
        onStart = actorRef ! message.Subscribe(subscriber),
        onComplete = actorRef ! message.Unsubscribe(subscriber),
        onError = (_,_) => actorRef ! message.Unsubscribe(subscriber)
      )
    enumerator
  }
  
}