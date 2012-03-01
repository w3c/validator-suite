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

    lazy val enumerator: PushEnumerator[message.ObservationUpdate] =
      Enumerator.imperative[message.ObservationUpdate]( onStart = run ! message.Subscribe(subscriber) )
    
//    def push(msg: message.ObservationUpdate): Unit =
//      try { 
//        enumerator.push(msg)
//      } catch { case e: java.nio.channels.ClosedChannelException =>
//        run ! message.Unsubscribe(subscriber)
//        enumerator.close
//      }
      
    enumerator
  }
  
}
