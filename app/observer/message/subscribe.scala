package org.w3.vs.observer.message

import akka.actor.ActorRef

case class Subscribe(subscriber: ActorRef)

case class Unsubscribe(subscriber: ActorRef)
