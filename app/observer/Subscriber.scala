package org.w3.vs.observer

import scala.collection.mutable.Set
import akka.actor.TypedActor
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import akka.actor.Props

/**
 * A Subscriber that can subscribe to an Observer
 * Then the Observer can broadcast message to the Subcriber
 */
trait Subscriber {
  def enumerator: Enumerator[message.BroadcastMessage]
  def subscribe(): Unit
  def unsubscribe(): Unit
  def broadcast(msg: message.BroadcastMessage): Unit
}

// TODO to be moved
class SubscriberImpl(observer: Observer) extends Subscriber {
  
  val enumerator = new PushEnumerator[message.BroadcastMessage]( onStart = this.subscribe() )
  
  def subscribe(): Unit = observer.subscribe(this)
  
  def unsubscribe(): Unit = observer.unsubscribe(this)
  
  def broadcast(msg: message.BroadcastMessage): Unit = enumerator.push(msg)
  
}