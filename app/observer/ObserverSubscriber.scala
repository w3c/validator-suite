package org.w3.vs.observer

import scala.collection.mutable.Set
import akka.actor.TypedActor
import org.w3.vs.GlobalSystem
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import akka.actor.Props

/**
 * A Subscriber that can subscribe to an Observer
 * Then the Observer can broadcast message to the Subcriber
 */
trait ObserverSubscriber {
  def enumerator: Enumerator[String]
  def subscribe(): Unit
  def unsubscribe(): Unit
  def broadcast(msg: String): Unit
}

// TODO to be moved
class Subscriber(observer: Observer) extends ObserverSubscriber {
  
  val enumerator = new PushEnumerator[String]( onStart = this.subscribe() )
  
  def subscribe(): Unit = observer.subscribe(this)
  
  def unsubscribe(): Unit = observer.unsubscribe(this)
  
  def broadcast(msg: String): Unit = enumerator.push(msg)
  
}