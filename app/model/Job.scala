package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import org.w3.util.akkaext._
import org.w3.vs.actor.message._
import org.w3.vs.exception._
import org.w3.vs.exception.SuiteException
import org.w3.vs.VSConfiguration
import akka.actor._
import akka.dispatch._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

case class JobVO(
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime = DateTime.now,
    lastCompleted: Option[DateTime] = None,
    creatorId: UserId,
    organizationId: OrganizationId,
    strategyId: StrategyId)

// closed with its strategy and lastData
case class Job(
    valueObject: JobVO,
    strategy: Strategy,
    lastData: Option[JobData] = None)(implicit conf: VSConfiguration) {

  import conf.system
  implicit def timeout = conf.timeout
  private val logger = Logger.of(classOf[Job])
  
  def id: JobId = valueObject.id
  def name: String = valueObject.name
  def createdOn: DateTime = valueObject.createdOn
  def lastCompleted: Option[DateTime] = valueObject.lastCompleted
  
  def getCreator = User.get(valueObject.creatorId)
  def getOrganization = Organization.get(valueObject.organizationId)
  def getStrategy = Strategy.get(valueObject.strategyId)
  def getHistory = JobData.getForJob(valueObject.id)
  
  def run(): Unit = PathAware(organizationsRef, path) ! Refresh
  
  def cancel(): Unit = PathAware(organizationsRef, path) ! Stop

  def on(): Unit = PathAware(organizationsRef, path) ! BeProactive

  def off(): Unit = PathAware(organizationsRef, path) ! BeLazy

  def health(): Int = {
    lastData match {
      case Some(data) => {
        val errorAverage = data.errors.toDouble / data.resources.toDouble
        (scala.math.exp(scala.math.log(0.5) / 10 * errorAverage) * 100).toInt
      }
      case _ => 0
    }
  }
  
  /*def jobData(): Future[JobData] =
    (PathAware(organizationsRef, path) ? GetJobData).mapTo[JobData]*/

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

  private val path = system / "organizations" / valueObject.organizationId.toString / "jobs" / id.toString

  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    PathAware(organizationsRef, path) ! message

  // A test needs this method to be public
  def listen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Listen(listener), listener)

  private def deafen(implicit listener: ActorRef): Unit =
    PathAware(organizationsRef, path).tell(Deafen(listener), listener)
    
}
    
object Job {
  
  def apply(
      id: JobId = JobId(),
      name: String,
      createdOn: DateTime = DateTime.now,
      lastCompleted: Option[DateTime] = None,
      creatorId: UserId,
      organizationId: OrganizationId,
      strategy: Strategy,
      lastData: Option[JobData] = None)(implicit conf: VSConfiguration): Job = 
    Job(JobVO(id, name, createdOn, lastCompleted, creatorId, organizationId, strategy.id), strategy, lastData)
  
  // Shouldn't be here. We need sets of initial data for dev and test modes
//  def fake(strategy: Strategy)(implicit configuration: VSConfiguration): Job = {
//    val fakeUser = User.fake
//    Job(name = "fake job", creatorId = fakeUser.id, organizationId = fakeUser.organizationId, strategy = strategy)
//  }
  
  def get(id: JobId): FutureVal[Exception, Job] = {
    /*import configuration.store
    //implicit def context = configuration.webExecutionContext
    store.getJobs(id = Some(id)).map(jobs => jobs.headOption).pureFold(
      f => Failure(f),
      {
        case Some(s) => Success(s)
        case _ => Failure(UnknownJob)
      }
    )*/
    sys.error("")
  }
  
  def getFor(user: User): FutureVal[Exception, Iterable[Job]] = sys.error("")
  
//  def getAll(id: OrganizationId)(implicit configuration: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
//    import configuration.store
//    //implicit def context = configuration.webExecutionContext
//    store.listJobs(id)
//  }
  
  def delete(id: JobId)/*(implicit configuration: VSConfiguration)*/: FutureVal[Exception, Unit] = {
    //import configuration.store
    //implicit def context = configuration.webExecutionContext
    //store.deleteJob(id = Some(id))
    sys.error("")
  }
  
  def save(job: Job)/*(implicit configuration: VSConfiguration)*/: FutureVal[Exception, Job] = {
//    import configuration.store
//    //implicit def context = configuration.webExecutionContext
//    store.createJob(
//      id = job.id,
//      name = job.name,
//      creatorId = job.creatorId,
//      organizationId = job.organizationId,
//      strategyId = job.strategy.id,
//      lastCompleted = None)
    sys.error("")
  }
  
//  def getAssertorResponses(
//    id: JobId,
//    after: Option[DateTime] = None)(
//    implicit configuration: VSConfiguration): FutureVal[Exception, Iterable[AssertorResponse]] = {
//      import configuration.store
//      //implicit def context = configuration.webExecutionContext
//      store.listAssertorResponses(id, after)
//    }
  
//  def withLastData(job: Job): Future[Job] = {
//    job.jobData().map(jobData => job.copy(lastData = Some(jobData)))
//  }
  
  def getForCreator(creator: UserId): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
  def getForOrganization(organization: OrganizationId): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
  def getForStrategy(strategy: StrategyId): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
}




