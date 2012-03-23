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
  
  // I think that the store should use typed exceptions (StoreException) instead of Throwables 
  // agree,   + FutureValidation and actor-based
  
  def get(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Option[JobConfiguration], Nothing, FALSE] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.getJobById(id).toDelayedValidation failMap (t => StoreException(t))
  }
    
  def getAll(id: OrganizationId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Iterable[JobConfiguration], Nothing, FALSE] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.listJobs(id).toDelayedValidation failMap (t => StoreException(t))
  }
  
  def delete(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, FALSE] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.removeJob(id).toDelayedValidation failMap (t => StoreException(t))
  }
  
  def save(job: JobConfiguration)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, FALSE] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.putJob(job).toDelayedValidation failMap (t => StoreException(t))
  }
  
  def getAssertorResults(
    id: JobId,
    after: Option[DateTime] = None)(
    implicit configuration: VSConfiguration): FutureValidation[SuiteException, Iterable[AssertorResult], Nothing, FALSE] = {
      import configuration.store
      implicit def context = configuration.webExecutionContext
      store.listAssertorResults(id, after).toDelayedValidation failMap (t => StoreException(t))
    }
  
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
