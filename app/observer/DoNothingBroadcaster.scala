package org.w3.vs.observer

trait DoNothingBroadcaster extends ObserverImpl {
  
  def subscribe(subscriber: ObserverSubscriber): Unit = ()
  def unsubscribe(subscriber: ObserverSubscriber): Unit = ()
  def broadcast(msg: BroadcastMessage): Unit = ()
  
}