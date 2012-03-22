package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.util._
import org.w3.vs.assertor._
import akka.actor._
import akka.dispatch._
import akka.pattern.ask
import akka.util.Duration
import akka.util.Timeout
import akka.util.duration._
import scalaz.Validation
import play.Logger
import play.api.libs.iteratee.{Enumerator, PushEnumerator}
import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import org.w3.vs.exception._
import org.w3.util._
import org.w3.util.Pimps._

object Job {
  
  def store = org.w3.vs.Prod.configuration.store
  
  // I think that the store should use typed exceptions (StoreException) instead of Throwables 
  
  def get(id: JobId): Validation[SuiteException, Option[JobConfiguration]] = 
    store getJobById id failMap (t => StoreException(t))
  
  def getAll(id: OrganizationId): Validation[SuiteException, Iterable[JobConfiguration]] =
    store listJobs id failMap (t => StoreException(t))
  
  def delete(id: JobId): Validation[SuiteException, Unit] =
    store removeJob id failMap (t => StoreException(t))
  
  def save(job: JobConfiguration): Validation[SuiteException, Unit] =
    store putJob job failMap (t => StoreException(t))
  
  def getAssertorResults(id: JobId, after: Option[DateTime] = None): Validation[SuiteException, Iterable[AssertorResult]] =
    store listAssertorResults (id, after) failMap (t => StoreException(t))
  
}

class Job(
  val configuration: JobConfiguration,
  val jobActorRef: ActorRef)(
  implicit conf: VSConfiguration,
  timeout: Timeout) {

  import conf.system

  val logger = play.Logger.of(classOf[Job])
  
  def id = configuration.id
  
  def refresh(): Unit = jobActorRef ! message.Refresh
  
  def stop(): Unit = jobActorRef ! message.Stop

  def on(): Unit = jobActorRef ! message.BeProactive

  def off(): Unit = jobActorRef ! message.BeLazy

  def jobData(): Future[JobData] =
    (jobActorRef ? message.GetJobData).mapTo[JobData]

  def subscribeToUpdates(): Enumerator[message.RunUpdate] = {
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: message.RunUpdate =>
          try { 
            enumerator.push(msg)
          } catch { 
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
          	case e => enumerator.close; logger.error("Socket exception: ", e)
          }
        case msg => logger.debug("subscriber got "+msg)
      }
    }))
    // TODO make the enumerator to stop the actor and unsubscribe it when an error occurs (or when it's 
    lazy val enumerator: PushEnumerator[message.RunUpdate] =
      Enumerator.imperative[message.RunUpdate](
        onStart = jobActorRef.tell(message.Subscribe, subscriber),
        onComplete = jobActorRef.tell(message.Unsubscribe, subscriber),
        onError = (_,_) => jobActorRef.tell(message.Unsubscribe, subscriber)
      )
    enumerator
  }
  
}
