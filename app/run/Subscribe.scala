package org.w3.vs.run

import scala.collection.mutable.Set
import akka.actor._
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import akka.actor.Props
import org.w3.vs.ValidatorSuiteConf

object Subscribe {
  
  val logger = play.Logger.of("Subscribe")
  
  def to(run: ActorRef)(implicit conf: ValidatorSuiteConf): Enumerator[message.ObservationUpdate] = {
    import conf.system
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: message.ObservationUpdate =>
          try { 
            enumerator.push(msg)
          } catch { case e: java.nio.channels.ClosedChannelException =>
            run ! message.Unsubscribe(subscriber)
            enumerator.close
          }
        case msg => logger.debug("subscriber got "+msg)
      }
    }))

    // TODO make the enumerator to stop the actor and unsubscribe it when an error occurs (or when it's 
    lazy val enumerator: PushEnumerator[message.ObservationUpdate] =
      Enumerator.imperative[message.ObservationUpdate]( onStart = run ! message.Subscribe(subscriber) )
    
    enumerator
  }
  
}
