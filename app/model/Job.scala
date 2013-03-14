package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import akka.actor._
import akka.pattern.{ ask, AskTimeoutException }
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import org.w3.util.Util.journalCommit
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor._
import scala.util.{ Success, Failure, Try }
import scala.concurrent.duration.Duration
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.exception.UnknownJob
import org.w3.vs.view.model.JobView
import scalaz.Scalaz._

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.ReactiveBSONImplicits._
// Play Json imports
import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

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
  latestDone: Option[Done]) { thisJob =>

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

  import scala.reflect.ClassTag
  def actorBasedEnumerator[T](forever: Boolean)(implicit classifier: Classifier[T], classTag: ClassTag[T], conf: VSConfiguration): Enumerator[T] = {
    import conf._
    val (enumerator, channel) = Concurrent.broadcast[T]
    def subscriberActor(): Actor = new Actor {
      def stop(): Unit = {
        channel.eofAndEnd()
        context.stop(self)
      }
      def push(msg: T): Unit = {
        try {
          channel.push(msg)
        } catch { case t: Throwable =>
          logger.error("Enumerator exception", t)
          stop()
        }
      }
      def subscribeToJobActor(actorRef: ActorRef): Unit = {
        actorRef ! JobActor.Listen(self, classifier)
      }
      override def preStart(): Unit = {
        // subscribe to the EventStream to be notified of new started runs
        if (forever) {
          system.eventStream.subscribe(self, classOf[JobActorStarted])
        }
        // subscribe to the JobActor itself
        thisJob.status match {
          // if the Job is currently running, just talk to it directly
          case Running(_, jobActorPath) => subscribeToJobActor(system.actorFor(jobActorPath))
          // but if it's not running right now, then the status may
          // have changed. Let's give it a chance. Note that we
          // shouldn't be missing anything at this point as we've
          // already subscribed to the EventStream
          case _ => Job.get(id).onSuccess { _.status match {
            case Running(_, jobActorPath) => subscribeToJobActor(system.actorFor(jobActorPath))
            case _ => ()
          }}
        }
      }
      /** the actor's main method... */
      def receive = {
        // the normal messages that we're expecting
        case msg: T => push(msg)
        // the actor can group the messages in an iterable
        case messages: Iterable[_] => messages foreach { case msg: T => push(msg) }
        // this can come from the EventStream
        case JobActorStarted(_, `id`, _, jobActorRef) => subscribeToJobActor(jobActorRef)
        // that's the signal that a run just ended
        case () => if (forever) channel.push(Input.Empty) else stop()
        case msg => logger.error("subscriber got " + msg) ; stop()
      }
    }
    // create (and start) the actor
    system.actorOf(Props(subscriberActor()))
    enumerator
  }

  def runEvents()(implicit conf: VSConfiguration): Enumerator[RunEvent] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Enumerator[RunEvent]()
      case Done(runId, _, _, _) => Run.enumerateRunEvents(runId)
      case Running(_, jobActorPath) => actorBasedEnumerator[RunEvent](forever = false)
    }
  }

  /** Enumerator for all the JobData-s, even for future runs.  This is
    * stateless.  If you just want the most up-to-date JobData, use
    * Job.jobData() instead. */
  def jobDatas()(implicit conf: VSConfiguration): Enumerator[JobData] = {
    import conf._
    val enumerator = actorBasedEnumerator[RunData](true)
    enumerator &> Enumeratee.map(runData => JobData(this, runData))
  }

  /** returns the most up-to-date JobData for the Job, if available */
  def getJobData()(implicit conf: VSConfiguration): Future[JobData] = {
    getRunData().map { JobData(this, _) }
  }

  /** this is stateless, so if you're the Done case, you want to use
    * Job.runData() instead */
  def runDatas()(implicit conf: VSConfiguration): Enumerator[RunData] = {
    actorBasedEnumerator[RunData](true)
  }

  /** returns the most up-to-date RunData for the Job, if available */
  def getRunData()(implicit conf: VSConfiguration): Future[RunData] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(RunData())
      case Done(_, _, _, runData) => Future.successful(runData)
      case Running(_, jobActorPath) =>
        val actorRef = system.actorFor(jobActorPath)
        val message = JobActor.Listen(null, Classifier.SubscribeToRunData)
        val shortTimeout = Duration(1, "s")
        ask(actorRef, message)(shortTimeout).mapTo[RunData] recover {
          case _: AskTimeoutException => RunData()
        }
    }
  }

  /* *********** */

  def resourceDatas()(implicit conf: VSConfiguration): Enumerator[ResourceData] = {
    actorBasedEnumerator[ResourceData](true)
  }

  def resourceDatas(url: URL)(implicit conf: VSConfiguration): Enumerator[ResourceData] = ???

  def groupedAssertionDatas()(implicit conf: VSConfiguration): Enumerator[GroupedAssertionData] = ???

  def assertionDatas(url: URL)(implicit conf: VSConfiguration): Enumerator[Assertion] = ???

}

object Job {

  def createNewJob(name: String, strategy: Strategy, creatorId: UserId): Job =
    Job(JobId(), name, DateTime.now(DateTimeZone.UTC), strategy, creatorId, NeverStarted, None)

  val logger = Logger.of(classOf[Job])

  def collection(implicit conf: VSConfiguration): BSONCollection =
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
    collection.update(selector, update, writeConcern = journalCommit) map { lastError =>
      if (!lastError.ok) throw lastError
    }

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
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
      case None => throw new NoSuchElementException("Invalid jobId: " + jobId)
      case Some(json) => json.as[Job]
    }
  }

  def getRunningJobs()(implicit conf: VSConfiguration): Future[List[Job]] = {
    val query = Json.obj("status.actorPath" -> Json.obj("$exists" -> JsBoolean(true)))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.toList() map { list => list map { _.as[Job] } }
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
    val cursor = collection.find(query).cursor[JsValue]
    cursor.toList() map { list =>
      list map { json => json.as[Job] }
    }
  }

  /** returns the Job for this JobId, if it belongs to the provided user
    * if not, it throws an UnknownJob exception */
  def getFor(userId: UserId, jobId: JobId)(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    val query = Json.obj("_id" -> toJson(jobId), "creator" -> toJson(userId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
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
        update = toJson(rebornJob),
        writeConcern = journalCommit
      ) map { lastError => job }
      update flatMap { case job =>
        job.status match {
          case Done(runId, _, _, _) if removeRunData => Run.removeAll(runId)
          case Running(runId, _) if removeRunData => Run.removeAll(runId)
          case _ => Future.successful(())
        }
      }
    }
  }

}

