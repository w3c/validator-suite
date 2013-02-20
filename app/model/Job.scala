package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import akka.actor._
import akka.pattern.ask
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor._
import scala.util.{ Success, Failure, Try }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.exception.UnknownJob

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._
import org.w3.vs.actor.AssertorCall

case class Job(
  id: JobId,
  name: String,
  createdOn: DateTime,
  /** the strategy to be used when creating the Run */
  strategy: Strategy,
  /** the identity of the the creator of this Job */
  creatorId: UserId,
  /** the status for this Job */
  status: JobStatus,
  /** if this job was ever done, the final state -- includes link to the concerned Run */
  latestDone: Option[Done]) {

  import Job.logger

  def getAssertions()(implicit conf: VSConfiguration): Future[List[Assertion]] = {
    status match {
      case NeverStarted | Zombie => Future.successful(List.empty)
      case Done(runId, _, _, _) => Run.getAssertions(runId)
      case Running(runId, _) => Run.getAssertions(runId)
    }
  }

  def getAssertionsForURL(url: URL)(implicit conf: VSConfiguration): Future[List[Assertion]] = {
    status match {
      case NeverStarted | Zombie => Future.successful(List.empty)
      case Done(runId, _, _, _) => Run.getAssertionsForURL(runId, url)
      case Running(runId, _) => Run.getAssertionsForURL(runId, url)
    }
  }

  def save()(implicit conf: VSConfiguration): Future[Job] =
    Job.save(this)
  
  def delete()(implicit conf: VSConfiguration): Future[Unit] = {
    cancel() flatMap { case () =>
      Job.delete(id)
    }
  }

  def reset(removeRunData: Boolean = true)(implicit conf: VSConfiguration): Future[Unit] =
    Job.reset(this.id, removeRunData)

  def run()(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    (runsActorRef ? RunsActor.RunJob(this)).mapTo[Running] map { running =>
      this.copy(status = running)
    }
  }

  // TODO we can actually look at the status before sending the message
  def resume()(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    (runsActorRef ? RunsActor.ResumeJob(this)).mapTo[Unit]
  }

  def cancel()(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    status match {
      case NeverStarted | Zombie => Future.successful(())
      case Done(_, _, _, _) => Future.successful(())
      case Running(_, actorPath) => {
        val actorRef = system.actorFor(actorPath)
        (actorRef ? JobActor.Cancel).mapTo[Unit]
      }
    }
  }

  // TODO: should be Future[Option[JobData]]
  def getJobData()(implicit conf: VSConfiguration): Future[JobData] = {
    import conf._
    status match {
      case NeverStarted | Zombie =>
        Future.successful(JobData())
      case Done(_, _, _, jobData) => Future.successful(jobData)
      case Running(_, actorPath) => {
        val actorRef = system.actorFor(actorPath)
        (actorRef ? JobActor.GetJobData).mapTo[JobData]
      }
    }
  }

  def enumerator()(implicit conf: VSConfiguration): Enumerator[RunUpdate] = {
    import conf._
    val (_enumerator, channel) = Concurrent.broadcast[RunUpdate]
    val subscriber: ActorRef = system.actorOf(Props(new Actor {
      def receive = {
        case msg: RunUpdate =>
          try {
            channel.push(msg)
          } catch { 
            case e: ClosedChannelException => {
              logger.error("ClosedChannel exception: ", e)
              channel.eofAndEnd()
            }
            case e => {
              logger.error("Enumerator exception: ", e)
              channel.eofAndEnd()
            }
          }
        case msg => logger.error("subscriber got " + msg)
      }
    }))
    // TODO
    Await.result(vsEvents.subscribe(subscriber, FromJob(id)), atMost = timeout.duration)
    _enumerator
  }

}

object Job {

  def createNewJob(name: String, strategy: Strategy, creatorId: UserId): Job =
    Job(JobId(), name, DateTime.now(DateTimeZone.UTC), strategy, creatorId, NeverStarted, None)

  val logger = Logger.of(classOf[Job])

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("jobs")

  def sample(implicit conf: VSConfiguration) = Job(
    JobId("50cb698f04ca20aa0283bc84"),
    "Sample report",
    DateTime.now(DateTimeZone.UTC),
    Strategy(
      entrypoint = URL("http://www.w3.org/"),
      linkCheck = false,
      maxResources = 10,
      filter = Filter(include = Everything, exclude = Nothing),
      assertorsConfiguration = AssertorsConfiguration.default),
    User.sample.id,
    NeverStarted,
    None)

  private def updateStatus(
    jobId: JobId,
    status: JobStatus,
    latestDoneOpt: Option[Done])(
    implicit conf: VSConfiguration): Future[Unit] = {
    val selector = Json.obj("_id" -> toJson(jobId))
    val update = latestDoneOpt match {
      case Some(latestDone) =>
        Json.obj(
          "$set" -> Json.obj(
            "status" -> toJson(status),
            "latestDone" -> toJson(latestDone)))
      case None =>
        Json.obj(
          "$set" -> Json.obj(
            "status" -> toJson(status)))
    }
    collection.update[JsValue, JsValue](selector, update) map { lastError => () }
  }

  def updateStatus(
    jobId: JobId,
    status: JobStatus,
    latestDone: Done)(
    implicit conf: VSConfiguration): Future[Unit] = {
    updateStatus(jobId, status, Some(latestDone))
  }

  def updateStatus(
    jobId: JobId,
    status: JobStatus)(
    implicit conf: VSConfiguration): Future[Unit] = {
    updateStatus(jobId, status, None)
  }

  // returns the Job with the jobId and optionally the latest Run* for this Job
  // the Run may not exist if the Job was never started
  def get(jobId: JobId)(implicit conf: VSConfiguration): Future[Job] = {
    val query = Json.obj("_id" -> toJson(jobId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.headOption map {
      case None => throw new NoSuchElementException("Invalid jobId: " + jobId)
      case Some(json) => json.as[Job]
    }
  }

  def getRunningJobs()(implicit conf: VSConfiguration): Future[List[Job]] = {
    val query = Json.obj("status.actorPath" -> Json.obj("$exists" -> JsBoolean(true)))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { list => list map { _.as[Job] } }
  }

  /** Resumes all the pending jobs (Running status) in the system.
    * The function itself is blocking and intended to be called when VS is (re-)started.
    * If resuming a Run fails (either an exception or a timeout) then the Job's status is updated to Zombie.
    */
  def resumeAllJobs()(implicit conf: VSConfiguration): Unit = {
    import org.w3.util.Util.FutureF
    val runningJobs = getRunningJobs().getOrFail()
    val duration = Duration("15s")
    runningJobs foreach { job =>
      val future = job.resume()
      try {
        logger.info(s"${job.id}: resuming -- wait up to ${duration}")
        Await.result(future, duration)
        logger.info(s"${job.id}: successfuly resumed")
      } catch {
        case t: Throwable =>
          logger.error(s"failed to resume ${job}", t)
          updateStatus(job.id, Zombie) onComplete {
            case Failure(f) =>
              logger.error(s"failed to update status of ${job.id} to Zombie", f)
            case Success(_) =>
              logger.info(s"${job.id} status is now Zombie. Restart the server to clean the global state.")
          }
      }
    }
  }

  def getFor(userId: UserId)(implicit conf: VSConfiguration): Future[Iterable[Job]] = {
    import conf._
    val query = Json.obj("creator" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { list =>
      list map { json => json.as[Job] }
    }
  }

  /** returns the Job for this JobId, if it belongs to the provided user
    * if not, it throws an UnknownJob exception */
  def getFor(userId: UserId, jobId: JobId)(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    val query = Json.obj("_id" -> toJson(jobId), "creator" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.headOption map {
      case None => throw UnknownJob(jobId)
      case Some(json) => json.as[Job]
    }
  }

  def save(job: Job)(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    val jobJson = toJson(job)
    collection.insert(jobJson) map { lastError => job }
  }

  def delete(jobId: JobId)(implicit conf: VSConfiguration): Future[Unit] = {
    val query = Json.obj("_id" -> toJson(jobId))
    collection.remove[JsValue](query) map { lastError => () }
  }

  def reset(jobId: JobId, removeRunData: Boolean = true)(implicit conf: VSConfiguration): Future[Unit] = {
    Job.get(jobId) flatMap { job =>
      job.cancel() // <- do not block!
      val rebornJob = job.copy(status = NeverStarted, latestDone = None)
      // as we don't change the jobId, this will override the previous one
      val update = collection.update(
        selector = Json.obj("_id" -> toJson(jobId)),
        update = toJson(rebornJob) ) map { lastError => job }
      update flatMap {  case job =>
        job.status match {
          case Done(runId, _, _, _) if removeRunData => Run.removeAll(runId)
          case Running(runId, _) if removeRunData => Run.removeAll(runId)
          case _ => Future.successful(())
        }
      }
    }
  }

}

