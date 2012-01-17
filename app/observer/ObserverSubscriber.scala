package org.w3.vs.observer

import scala.collection.mutable.Set
import akka.actor.TypedActor
import org.w3.vs.GlobalSystem
import play.api.libs.iteratee.CallbackEnumerator
import akka.actor.Props

/**
 * A Subscriber that can subscribe to an Observer
 * Then the Observer can broadcast message to the Subcriber
 */
trait ObserverSubscriber {
  def subscribe(): Unit
  def unsubscribe(): Unit
  def broadcast(msg: String): Unit
}

class Subscriber(
    callback: CallbackEnumerator[String],
    observer: Observer)
    extends ObserverSubscriber {
  
  subscribe()
  
  // subscribes to the actionManager at instantiation time
  def subscribe(): Unit = observer.subscribe(this)
  
  def unsubscribe(): Unit = observer.unsubscribe(this)
  
  def broadcast(msg: String): Unit = {
    callback.push(msg)
  }
  
}