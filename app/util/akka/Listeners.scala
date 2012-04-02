package org.w3.util.akkaext

import akka.actor._
import java.util.concurrent.ConcurrentSkipListSet
import scala.collection.JavaConversions._

sealed trait ListenerMessage
case class Listen(listener: ActorRef) extends ListenerMessage
case class Deafen(listener: ActorRef) extends ListenerMessage
case class WithListeners(f: (ActorRef) ⇒ Unit) extends ListenerMessage

trait Listeners { self: Actor ⇒

  protected val _logger = play.Logger.of(classOf[Listeners])

  protected val _listeners = new ConcurrentSkipListSet[ActorRef]

  protected def listenerHandler: Actor.Receive = {
    case Listen(l) ⇒ {
      _logger.debug(l.path + " listens " + self.self.path)
      if (_listeners add l) self.context watch l
    }
    case Deafen(l) ⇒ {
      _logger.debug(l.path + " stops listening " + self.self.path)
      if (_listeners remove l) self.context unwatch l
    }
    case WithListeners(f) ⇒ _listeners foreach f
    case Terminated(l) if _listeners contains l => {
      _logger.debug(l.path + " used to listen " + self.self.path + " but got Terminated")
      _listeners remove l
    }
  }

  protected def tellListeners(msg: Any): Unit = {
    _logger.debug(self.self.path + " is gossiping")
    _listeners foreach (_ ! msg)
  }

}
