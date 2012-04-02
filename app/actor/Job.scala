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
import message._

object Job {
  
  def apply(organizationId: OrganizationId, jobId: JobId)(implicit configuration: VSConfiguration): Job =
    new Job(organizationId, jobId)

  def apply(jobConfiguration: JobConfiguration)(implicit configuration: VSConfiguration): Job =
    Job(jobConfiguration.organization, jobConfiguration.id)

  // I think that the store should use typed exceptions (StoreException) instead of Throwables 
  // agree,   + FutureValidation and actor-based
  
  def get(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, JobConfiguration, Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.getJobById(id)
  }
  
  def getAll(id: OrganizationId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Iterable[JobConfiguration], Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.listJobs(id)
  }
  
  def delete(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.removeJob(id)
  }
  
  def save(job: JobConfiguration)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.putJob(job)
  }
  
  def getAssertorResults(
    id: JobId,
    after: Option[DateTime] = None)(
    implicit configuration: VSConfiguration): FutureValidation[SuiteException, Iterable[AssertorResult], Nothing, NOTSET] = {
      import configuration.store
      implicit def context = configuration.webExecutionContext
      store.listAssertorResults(id, after)
    }
  
}

class Job(organizationId: OrganizationId, jobId: JobId)(implicit conf: VSConfiguration) {

  import conf.system

  val logger = play.Logger.of(classOf[Job])

  implicit def timeout = conf.timeout

  val organizationsRef = system.actorFor(system / "organizations")
  
  def refresh(): Unit = organizationsRef ! Message(organizationId, jobId, message.Refresh)
  
  def stop(): Unit = organizationsRef ! Message(organizationId, jobId, message.Stop)

  def on(): Unit = organizationsRef ! Message(organizationId, jobId, message.BeProactive)

  def off(): Unit = organizationsRef ! Message(organizationId, jobId, message.BeLazy)

  def jobData(): Future[JobData] =
    (organizationsRef ? Message(organizationId, jobId, message.GetJobData)).mapTo[JobData]

  def subscribeToUpdates(): PushEnumerator[message.RunUpdate] = {
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: message.RunUpdate =>
          try { 
            enumerator.push(msg)
          } catch { 
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
            case e => enumerator.close; logger.error("Enumerator exception: ", e)
          }
        case msg => logger.debug("subscriber got "+msg)
      }
    }))
    lazy val enumerator: PushEnumerator[message.RunUpdate] =
      Enumerator.imperative[message.RunUpdate](
        onStart = () => organizationsRef.tell(Message(organizationId, jobId, message.Subscribe), subscriber),
        onComplete = () => organizationsRef.tell(Message(organizationId, jobId, message.Unsubscribe), subscriber),
        onError = (_,_) => () => organizationsRef.tell(Message(organizationId, jobId, message.Unsubscribe), subscriber)
      )
    enumerator
  }
  
}
