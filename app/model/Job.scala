package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import org.w3.util.akkaext.Deafen
import org.w3.util.akkaext.Listen
import org.w3.util.akkaext.PathAware
import org.w3.util.FutureValidation
import org.w3.util.NOTSET
import org.w3.vs.actor.message._
import org.w3.vs.exception.SuiteException
import org.w3.vs.VSConfiguration
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.dispatch.Future
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.PushEnumerator
import play.Logger
import org.w3.util.URL

object Job {
  
  // Shouldn't be here. We need sets of initial data for dev and test modes
  def fake(strategy: Strategy)(implicit configuration: VSConfiguration): Job = {
    val fakeUser = User.fake
    Job(name = "fake job", creatorId = fakeUser.id, organizationId = fakeUser.organization, strategy = strategy)
  }
  
  def get(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Job, Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.getJobById(id)
  }
  
  def getAll(id: OrganizationId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Iterable[Job], Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.listJobs(id)
  }
  
  def delete(id: JobId)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, NOTSET] = {
    import configuration.store
    implicit def context = configuration.webExecutionContext
    store.removeJob(id)
  }
  
  def save(job: Job)(implicit configuration: VSConfiguration): FutureValidation[SuiteException, Unit, Nothing, NOTSET] = {
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
  
  def withLastData(job: Job)(implicit conf: VSConfiguration): Future[(Job, JobData)] =
    new JobW(job).jobData().map(jobData => (job, jobData))

  implicit def wrapJob(job: Job)(implicit conf: VSConfiguration): JobW = new JobW(job)

}

class JobW(job: Job)(implicit conf: VSConfiguration) {

  import job._

  import conf.system
  
  implicit def timeout = conf.timeout
  
  private val logger = Logger.of(classOf[Job])
  
  def run(): Unit = PathAware(organizationsRef, path) ! Refresh
  
  def cancel(): Unit = PathAware(organizationsRef, path) ! Stop

  def on(): Unit = PathAware(organizationsRef, path) ! BeProactive

  def off(): Unit = PathAware(organizationsRef, path) ! BeLazy

  def jobData(): Future[JobData] =
    (PathAware(organizationsRef, path) ? GetJobData).mapTo[JobData]

  def subscribeToUpdates(): Enumerator[RunUpdate] = {
    lazy val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try { 
            enumerator.push(msg)
          } catch { 
            case e: ClosedChannelException => enumerator.close; logger.error("ClosedChannel exception: ", e)
            case e => enumerator.close; logger.error("Enumerator exception: ", e)
          }
        case msg => logger.debug("subscriber got "+msg)
      }
    }))
    lazy val enumerator: PushEnumerator[RunUpdate] =
      Enumerator.imperative[RunUpdate](
        onComplete = () => {deafen(subscriber); logger.info("onComplete")},
        onError = (_,_) => () => {deafen(subscriber); logger.info("onError")}
      )
    listen(subscriber)
    enumerator &> Enumeratee.onIterateeDone(() => {deafen(subscriber); logger.info("onIterateeDone")})
  }
  
  
  // Following might be moved to a trait, e.g. ActorInterface?
  
  private val organizationsRef = system.actorFor(system / "organizations")

  private val path = system / "organizations" / organizationId.toString / "jobs" / id.toString

  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    PathAware(organizationsRef, path) ! message

  // A test needs this method to be public
  def listen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Listen(listener), listener)

  private def deafen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Deafen(listener), listener)

}

case class Job(
    id: JobId = JobId(),
    name: String,
    creatorId: UserId,
    organizationId: OrganizationId,
    strategy: Strategy,
    createdOn: DateTime = DateTime.now)
