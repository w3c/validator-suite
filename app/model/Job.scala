package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.DateTime
import org.w3.util.akkaext._
import org.w3.vs.actor.message._
import org.w3.vs.exception._
import org.w3.vs.VSConfiguration
import akka.actor._
import akka.dispatch._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Validation._
import scalaz.Scalaz._
import scalaz._

// closed with its strategy and lastData
case class Job (
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime = DateTime.now,
    creatorId: UserId,
    organizationId: OrganizationId,
    strategy: Strategy,
    lastData: Option[JobData] = None)(implicit conf: VSConfiguration) {

  import conf.system
  implicit def timeout = conf.timeout
  private val logger = Logger.of(classOf[Job])
  
  def toValueObject: JobVO = JobVO(id, name, createdOn, creatorId, organizationId, strategy.id)
  
  def getCreator: FutureVal[Exception, User] = User.get(creatorId)
  def getOrganization: FutureVal[Exception, Organization] = Organization.get(organizationId)
  def getHistory: FutureVal[Exception, Iterable[JobData]] = JobData.getForJob(id)
  def getActivity(implicit context: ExecutionContext): FutureVal[Throwable, RunActivity] = (PathAware(organizationsRef, path).?[RunActivity](GetActivity))
  
  def getLastRunAssertions: FutureVal[Exception, Iterable[Assertion]] = sys.error("")
  
  // save jobdata too
  def save(): FutureVal[Exception, Job] = Job.save(this)
  def delete(): FutureVal[Exception, Unit] = {
    cancel()
    Job.delete(id)
  }
  
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

object Job {

  def get(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Job] = sys.error("")
  def getFor(user: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = {
    val w3 = Job(
      createdOn = DateTime.now,
      name = "W3C",
      creatorId = UserId(),
      organizationId = OrganizationId(),
      strategy = Strategy(
        entrypoint = URL("http://www.w3.org/"),
        linkCheck = false,
        maxResources = 100,
        filter = Filter(include = Everything, exclude = Nothing)))
    implicit def ec = conf.webExecutionContext
    FutureVal.successful(Iterable(w3))
  }
  def getFor(organization: OrganizationId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
  def getFor(strategy: StrategyId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
  def getCreatedBy(creator: UserId)(implicit conf: VSConfiguration): FutureVal[Exception, Iterable[Job]] = sys.error("ni")
  def save(job: Job)(implicit conf: VSConfiguration): FutureVal[Exception, Job] = {
    implicit def ec = conf.webExecutionContext
    FutureVal.failed(new Exception("Not implemented yet"))
  }
  
  def delete(id: JobId)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = sys.error("")

}

