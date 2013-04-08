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

  def getAssertions()(implicit conf: VSConfiguration): Future[Iterable[Assertion]] = {
    status match {
      case NeverStarted | Zombie => Future.successful(Iterable.empty)
      case Done(runId, _, _, _) => Run.getAssertions(runId)
      case Running(runId, _) => Run.getAssertions(runId)
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

  /** asks the actor at `actorPath` for the optional value corresponding
    * to `classifier`. The actor sends back a NoSuchElementException
    * to signal that no value can be provided. If nothing comes back
    * on time, the TimeoutException is mapped to a
    * NoSuchElementException.
    */
  def getFuture(actorPath: ActorPath, classifier: Classifier)(implicit classTag: ClassTag[classifier.OneOff], conf: VSConfiguration): Future[classifier.OneOff] = {
    import conf._
    val actorRef = system.actorFor(actorPath)
    val message = JobActor.Get(classifier)
    val shortTimeout = Duration(1, "s")
    ask(actorRef, message)(shortTimeout).mapTo[classifier.OneOff] recoverWith {
      case _: AskTimeoutException => Future.failed[classifier.OneOff](new NoSuchElementException)
    }
  }

  /** enumerates the values of type T (according to the
    * `classifier`). This will spawn an anonymous actor which will
    * subscribe to the current JobActor (if available) and future
    * ones. This actor detects the new runs/JobActor by subscribing to
    * the system's EventStream. The JobActor sends () values when runs
    * are over, and depending on the value for `forever`, this
    * enumerator will either terminates (Input.EOF) or just emit a
    * Input.Empty.
    */
  def actorBasedEnumerator(classifier: Classifier, forever: Boolean)(implicit classTag: ClassTag[classifier.Streamed], conf: VSConfiguration): Enumerator[Iterator[classifier.Streamed]] = {
    import conf._
    val (enumerator, channel) = Concurrent.broadcast[Iterator[classifier.Streamed]]
    def subscriberActor(): Actor = new Actor {

      var channel: Concurrent.Channel[Iterator[classifier.Streamed]] = null

      def stop(): Unit = {
        context.stop(self)
        if (channel != null)
          channel.eofAndEnd()
      }

      def push(msg: Iterator[classifier.Streamed]): Unit = {
        try {
          if (channel != null)
            channel.push(msg)
        } catch { case t: Throwable =>
          logger.error("Enumerator exception", t)
          stop()
        }
      }

      def subscribeToJobActor(actorRef: ActorRef): Unit = {
        actorRef ! JobActor.Listen(classifier)
      }

      def subscribeToChannel(channel: Concurrent.Channel[Iterator[classifier.Streamed]]): Unit = {
        this.channel = channel
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
      /* the actor's main method... @@@@ */
      def receive = {
        // the normal messages that we're expecting
        case classTag(msg) => push(Iterator(msg))
        // the actor can group the messages in an iterable
        case messages: Iterable[_] =>
          push(messages.asInstanceOf[Iterable[classifier.Streamed]].iterator)
        // this can come from the EventStream
        case JobActorStarted(_, `id`, _, jobActorRef) => subscribeToJobActor(jobActorRef)
        // that's the signal that a run just ended
        case () => if (forever) channel.push(Input.Empty) else stop()
        case channel: Concurrent.Channel[_] =>
          subscribeToChannel(channel.asInstanceOf[Concurrent.Channel[Iterator[classifier.Streamed]]])
        case msg => logger.error("subscriber got " + msg) ; stop()
      }
    }
    // create (and start) the actor
    val actor = system.actorOf(Props(subscriberActor()))
    Concurrent.unicast(
      onStart = { channel => actor ! channel },
      onComplete = { actor ! PoisonPill },
      onError = { case (error, input) =>
        logger.error(s"$input: $error")
        actor ! PoisonPill
      }
    )
  }

  /* Conventions/guidelines in the naming for the following methods.
   * 
   * `X` denotes the type/message that we're interested in.
   * 
   * - def Xs(): Enumerator[X] --> an Enumerator for the X-s. The
   *   JobActor will try to respond as quickly as possible with
   *   something up-to-date, or with the entire history (it depends on
   *   the kind of X). Note: the Enumerator stays up even for new
   *   runs! (not the case for RunEvent). When that happens, it emits
   *   an Input.Empty. The Enumerator should terminate with Input.EOF
   *   only when the Job is deleted (that's a TODO for now).
   * 
   * - def getX(): Future[X] --> the most up-to-date value for this
   *   X. This is used for the stateless events.
   * 
   * - def getXs(): Future[Iterable[X]] --> the history of Xs for the
   *   latest Run. This is intended to be as fast as possible, so it
   *   can be used for static pages.
   * 
   *  Note: of course, the methods can be further constrained with
   *  parameters (eg. filter X-s for a specific URL).
   */

  def runEvents()(implicit conf: VSConfiguration): Enumerator[Iterator[RunEvent]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Enumerator()
      case Done(runId, _, _, _) => Run.enumerateRunEvents(runId)
      case Running(_, jobActorPath) =>
        actorBasedEnumerator(Classifier.AllRunEvents, forever = false)
    }
  }

  /** Enumerator for all the JobData-s, even for future runs.  This is
    * stateless.  If you just want the most up-to-date JobData, use
    * Job.jobData() instead. */
  def jobDatas()(implicit conf: VSConfiguration): Enumerator[Iterator[JobData]] = {
    import conf._
    runDatas() &> Enumeratee.map(_.map(runData => JobData(this, runData)))
  }

  /** returns the most up-to-date JobData for the Job, if available */
  def getJobData()(implicit conf: VSConfiguration): Future[JobData] = {
    getRunData().map { JobData(this, _) }
  }

  /** this is stateless, so if you're the Done case, you want to use
    * Job.runData() instead */
  def runDatas()(implicit conf: VSConfiguration): Enumerator[Iterator[RunData]] = {
    def enumerator = actorBasedEnumerator(Classifier.AllRunDatas, forever = true)
    this.status match {
      case Done(_, _, _, runData) =>
        Enumerator(Iterator(runData)) andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  /** returns the most up-to-date RunData for the Job, if available */
  def getRunData()(implicit conf: VSConfiguration): Future[RunData] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(RunData())
      case Done(_, _, _, runData) => Future.successful(runData)
      case Running(_, jobActorPath) =>
        getFuture(jobActorPath, Classifier.AllRunDatas) recover {
          case _: NoSuchElementException => RunData()
        }
    }
  }

  def resourceDatas()(implicit conf: VSConfiguration): Enumerator[Iterator[ResourceData]] = {
    def enumerator = actorBasedEnumerator(Classifier.AllResourceDatas, forever = true)
    this.status match {
      case Done(runId, _, _, _) =>
        val current: Enumerator[Iterator[ResourceData]] =
          Enumerator(Run.getResourceDatas(runId)) &> Enumeratee.mapM(x => x.map(_.iterator))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all ResourceDatas updates for url
  def resourceDatas(url: URL)(implicit conf: VSConfiguration): Enumerator[ResourceData] = {
    def enumerator = actorBasedEnumerator(Classifier.ResourceDataFor(url), forever = true) &> Enumeratee.mapConcat(_.toSeq)
    this.status match {
      case Done(runId, _, _, _) =>
        val current: Enumerator[ResourceData] =
          Enumerator(Run.getResourceDataForURL(runId, url)) &> Enumeratee.mapM(x => x)
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // the most up-to-date ResourceData for url
  def getResourceData(url: URL)(implicit conf: VSConfiguration): Future[ResourceData] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.failed(new NoSuchElementException)
      case Done(runId, _, _, _) => Run.getResourceDataForURL(runId, url)
      case Running(_, jobActorPath) =>
        getFuture(jobActorPath, Classifier.ResourceDataFor(url))
    }
  }

  // all current ResourceDatas
  def getResourceDatas()(implicit conf: VSConfiguration): Future[Iterable[ResourceData]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getResourceDatas(runId)
      case Running(_, jobActorPath) =>
        getFuture(jobActorPath, Classifier.AllResourceDatas)
    }
  }

  // all GroupedAssertionDatas updates
  def groupedAssertionDatas()(implicit conf: VSConfiguration): Enumerator[Iterator[GroupedAssertionData]] =  {
    def enumerator = actorBasedEnumerator(Classifier.AllGroupedAssertionDatas, forever = true)
    this.status match {
      case Done(runId, _, _, _) =>
        val current: Enumerator[Iterator[GroupedAssertionData]] =
          Enumerator(Run.getGroupedAssertionDatas(runId)) &> Enumeratee.mapM(x => x.map(_.iterator))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all current GroupedAssertionDatas
  def getGroupedAssertionDatas()(implicit conf: VSConfiguration): Future[Iterable[GroupedAssertionData]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getGroupedAssertionDatas(runId)
      case Running(_, jobActorPath) =>
        getFuture(jobActorPath, Classifier.AllGroupedAssertionDatas)
    }
  }

  // all Assertions updatesfor url
  def assertions(url: URL)(implicit conf: VSConfiguration): Enumerator[Iterator[Assertion]] = {
    def enumerator = actorBasedEnumerator(Classifier.AssertionsFor(url), forever = true)
    this.status match {
      case Done(runId, _, _, _) =>
        val current: Enumerator[Iterator[Assertion]] =
          Enumerator(Run.getAssertionsForURL(runId, url)) &> Enumeratee.mapM(x => x.map(_.iterator))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all current Assertions for `url`
  def getAssertions(url: URL)(implicit conf: VSConfiguration): Future[Seq[Assertion]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getAssertionsForURL(runId, url)
      case Running(_, jobActorPath) =>
        getFuture(jobActorPath, Classifier.AssertionsFor(url))
    }
  }

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

  /** the list of all the Jobs */
  def getAll()(implicit conf: VSConfiguration): Future[List[Job]] = {
    val cursor = collection.find(Json.obj()).cursor[JsValue]
    cursor.toList() map { list => list map { _.as[Job] } }
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
    val query = Json.obj("_id" -> toJson(jobId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption() map {
      case Some(json) if (json \ "creator").as[UserId] === userId => json.as[Job]
      case _ => throw UnknownJob(jobId)
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

