package org.w3.vs.actor

import akka.actor._
import akka.pattern.ask
import akka.event._
import akka.util.Timeout
import org.w3.vs.model._
import scala.util._
import scalaz.Scalaz._
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.ValidatorSuite

object RunEventBusActor {

  case class Listen(subscriber: ActorRef, provenance: MessageProvenance)
  case class Deafen(subscriber: ActorRef)
  case class Publish(event: RunEvent)

}

class RunEventBusActor() extends Actor {

  import RunEventBusActor.{ Listen, Deafen, Publish }

  val underlying =
    new EventBus
    with ActorEventBus /* Represents an EventBus where the Subscriber type is ActorRef */
    with ScanningClassification /* Maps Classifiers to Subscribers */ {

      type Classifier = MessageProvenance
      type Event = RunEvent

      protected def compareClassifiers(a: MessageProvenance, b: MessageProvenance): Int = (a, b) match {
        case (FromAll, _) => -1
        case (_, FromAll) => 1
        case (FromUser(u1), FromUser(u2)) => u1.toString compareTo u2.toString
        case (FromUser(_), _) => -1
        case (FromJob(j1), FromJob(j2)) => j1.toString compareTo j2.toString
        case (FromJob(_), FromUser(_)) => 1
        case (FromJob(_), FromRun(_)) => -1
        case (FromRun(r1), FromRun(r2)) => r1.toString compareTo r2.toString
        case (FromRun(_), _) => 1
      }

      protected def matches(classifier: MessageProvenance, event: RunEvent): Boolean =
        classifier match {
          case FromAll => true
          case FromUser(userId) => event.userId.map(userId === _).getOrElse(false)
          case FromJob(jobId) => jobId === event.jobId
          case FromRun(runId) => runId === event.runId
        }

      protected def publish(event: Event, subscriber: ActorRef): Unit =
        subscriber ! event

    }

  def receive = {
    case Publish(event) => {
      underlying.publish(event)
    }
    case Listen(subscriber, provenance) => {
      context.watch(subscriber)
      val b = underlying.subscribe(subscriber, provenance)
      sender ! b
    }
    case Deafen(subscriber) => {
      context.unwatch(subscriber)
      underlying.unsubscribe(subscriber)
    }
    case Terminated(ref) => {
      underlying.unsubscribe(ref)
    }
  }

}

object RunEventBus {

  import RunEventBusActor.{ Listen, Deafen, Publish }

  def apply(actorRef: ActorRef): RunEventBus = new RunEventBus(actorRef)

}

class RunEventBus(actorRef: ActorRef) extends EventBus {

  type Classifier = MessageProvenance
  type Event = RunEvent
  type Subscriber = ActorRef

  import RunEventBusActor._

  import scala.concurrent.ExecutionContext.Implicits.global
  val duration = Duration(1, "s")
  implicit val timeout = akka.util.Timeout(duration)

  def publish(event: RunEvent): Unit =
    actorRef ! Publish(event)

  def subscribe(subscriber: ActorRef): Boolean =
    subscribe(subscriber, FromAll)

  def subscribe(subscriber: ActorRef, provenance: MessageProvenance): Boolean = {
    val f = (actorRef ? Listen(subscriber, provenance)).mapTo[Boolean]
    Await.result(f, duration)
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    actorRef ! Deafen(subscriber)
  }

  def unsubscribe(subscriber: ActorRef, provenance: MessageProvenance): Boolean = ???

}
