package org.w3.vs.actor

import akka.actor._
import akka.pattern.ask
import akka.event._
import akka.util.Timeout
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._
import scala.concurrent._
// import scala.concurrent.ExecutionContext.Implicits.global

/** an [[EventBus]] specialized for [[RunUpdate]]s */
class VSEventBus() extends EventBus
with ActorEventBus /* Represents an EventBus where the Subscriber type is ActorRef */
with ScanningClassification /* Maps Classifiers to Subscribers */ {

  type Event = RunUpdate
  type Classifier = MessageProvenance

  protected def compareClassifiers(a: MessageProvenance, b: MessageProvenance): Int = (a, b) match {
    case (FromUser(u1), FromUser(u2)) => u1.toString compareTo u2.toString
    case (FromUser(_), _) => -1
    case (FromJob(j1), FromJob(j2)) => j1.toString compareTo j2.toString
    case (FromJob(_), FromUser(_)) => 1
    case (FromJob(_), FromRun(_)) => -1
    case (FromRun(r1), FromRun(r2)) => r1.toString compareTo r2.toString
    case (FromRun(_), _) => 1
  }

  protected def matches(classifier: MessageProvenance, event: RunUpdate): Boolean =
    classifier match {
      case FromUser(userId) => userId === event.userId
      case FromJob(jobId) => jobId === event.jobId
      case FromRun(runId) => runId === event.runId
    }

  protected def publish(event: Event, subscriber: ActorRef): Unit =
    subscriber ! event

}

object VSEventsActor {

  val logger = play.Logger.of(classOf[VSEventsActor])

  case class Listen(subscriber: ActorRef, provenance: MessageProvenance)
  case class Deafen(subscriber: ActorRef)
  case class Publish(message: RunUpdate)

}

class VSEventsActor() extends Actor {

  import VSEventsActor.{ logger, Listen, Deafen, Publish }

  val eventbus = new VSEventBus

  def receive = {
    case Publish(message) => {
      eventbus.publish(message)
    }
    case Listen(subscriber, provenance) => {
      context.watch(subscriber)
      eventbus.subscribe(subscriber, provenance)
      sender ! ()
    }
    case Deafen(subscriber) => {
      context.unwatch(subscriber)
      eventbus.unsubscribe(subscriber)
    }
    case Terminated(ref) => {
      eventbus.unsubscribe(ref)
    }
  }

}

object VSEvents {

  import VSEventsActor.{ Listen, Deafen, Publish }

  def apply(actorRef: ActorRef) = new VSEvents {
    def publish(message: RunUpdate): Unit =
      actorRef ! Publish(message)
    def subscribe(subscriber: ActorRef, provenance: MessageProvenance)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] =
      (actorRef ? Listen(subscriber, provenance)).mapTo[Unit]
    def unsubscribe(subscriber: ActorRef): Unit =
      actorRef ! Deafen(subscriber)
  }

}

trait VSEvents {
  def publish(message: RunUpdate): Unit
  def subscribe(subscriber: ActorRef, provenance: MessageProvenance)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit]
  def unsubscribe(subscriber: ActorRef): Unit
}
