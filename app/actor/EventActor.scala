package org.w3.vs.actor

import akka.actor._
import akka.event._
import org.w3.util.akkaext._
import org.w3.vs.VSConfiguration
import org.w3.vs.model._
import org.w3.vs.actor.message._
import scala.util._
import scalaz.Scalaz._

sealed trait MessageProvenance
case class FromUser(userId: UserId) extends MessageProvenance
case class FromJob(jobId: JobId) extends MessageProvenance
case class FromRun(runId: RunId) extends MessageProvenance

final class VSEventBus extends EventBus
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

  protected def matches(classifier: MessageProvenance, event: RunUpdate): Boolean = ???

  protected def publish(event: Event, subscriber: ActorRef): Unit =
    subscriber ! event

}

object EventActor {

  val logger = play.Logger.of(classOf[EventActor])

  sealed trait ListenerMessage
  case class Listen(subscriber: ActorRef, to: MessageProvenance) extends ListenerMessage
  case class Deafen(subscriber: ActorRef) extends ListenerMessage

}

class EventActor() extends Actor {

  def receive = ???

}
