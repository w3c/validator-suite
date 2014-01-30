package org.w3.vs.model

import org.joda.time.{DateTime, DateTimeZone}
import akka.pattern.{ask, AskTimeoutException}
import play.api.libs.iteratee._
import play.Logger
import org.w3.vs.util._
import org.w3.vs.store.MongoStore.journalCommit
import org.w3.vs.web._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor._
import scala.util.{Success, Failure, Try}
import scala.concurrent.duration.Duration
import scala.concurrent.{ops => _, _}
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.exception.{AccessNotAllowed, UnknownJob}
import org.w3.vs.view.model.JobView
import scalaz.Scalaz._
import play.modules.reactivemongo.json.collection.JSONCollection
import akka.actor._
import reactivemongo.core.commands.Count
import reactivemongo.bson.BSONDocument

// Play Json imports

import play.api.libs.json._
import Json.toJson
import org.w3.vs.store.Formats._

case class Job(
  id: JobId = JobId(),
  name: String,
  /**the strategy to be used when creating the Run */
  strategy: Strategy,
  /**the identity of the the creator of this Job, None if it is a OneTime public job */
  creatorId: Option[UserId],
  // whether this job is publicly available
  isPublic: Boolean = false,
  /**the status for this Job */
  status: JobStatus = NeverStarted,
  /**if this job was ever done, the final state -- includes link to the concerned Run */
  latestDone: Option[Done] = None,
  createdOn: DateTime =  DateTime.now(DateTimeZone.UTC)) {
  thisJob =>

  import Job.logger

  assert(isPublic == true || creatorId != None, "A Job cannot be both private and anonymous")

  def maxPages = strategy.maxResources

  def withEntrypoint(url: URL) = copy(strategy = strategy.copy(entrypoint = url))

  def compactString = {
    val public = if (isPublic) "Public " else "Private"
    def pad(s: String, padding: Int) = {
      s + " "*(scala.math.max(0, padding - s.length))
    }
    val stat = status match {
      case NeverStarted => NeverStarted.toString
      case Zombie => Zombie.toString
      case Running(runId, actorName) => "Running"
      case Done(runId, reason, completedOn, runData) => s"Done(${completedOn})"
    }
    s"${id} - MaxPages: ${pad(strategy.maxResources.toString, 4)} - ${public} - ${creatorId.map(u => "User: " + u.id).getOrElse("Anonymous")} - ${stat} - ${strategy.entrypoint} - ${name} "
  }

  def getAssertions()(implicit conf: ValidatorSuite with Database): Future[Iterable[Assertion]] = {
    status match {
      case NeverStarted | Zombie => Future.successful(Iterable.empty)
      case Done(runId, _, _, _) => Run.getAssertions(runId)
      case Running(runId, _) => Run.getAssertions(runId)
    }
  }

  def save()(implicit conf: ValidatorSuite with Database): Future[Job] =
    Job.save(this)

  def delete()(implicit conf: ValidatorSuite): Future[Unit] = {
    cancel() flatMap {
      case () =>
        Job.delete(id)
    }
  }

  def reset(removeRunData: Boolean = true)(implicit conf: ValidatorSuite): Future[Unit] =
    Job.reset(this.id, removeRunData)

  def run()(implicit vs: ValidatorSuite): Future[Job] = {
    implicit val timeout = vs.timeout
    (vs.runsActorRef ? RunsActor.RunJob(this)).mapTo[Running] map {
      running =>
        this.copy(status = running)
    }
  }

  // TODO we can actually look at the status before sending the message
  def resume()(implicit conf: ValidatorSuite): Future[Unit] = {
    implicit val timeout = conf.timeout
    (conf.runsActorRef ? RunsActor.ResumeJob(this)).mapTo[Unit]
  }

  def cancel()(implicit conf: ValidatorSuite): Future[Unit] = {
    implicit val timeout = conf.timeout
    status match {
      case NeverStarted | Zombie => Future.successful(())
      case Done(_, _, _, _) => Future.successful(())
      case Running(_, actorName) => {
        conf.system.actorSelection(actorName.actorPath).resolveOne().flatMap(
          actor => (actor ? JobActor.Cancel).mapTo[Unit]
        )
      }
    }
  }

  import scala.reflect.ClassTag

  /**asks the actor at `actorPath` for the optional value corresponding
   * to `classifier`. The actor sends back a NoSuchElementException
   * to signal that no value can be provided. If nothing comes back
   * on time, the TimeoutException is mapped to a
   * NoSuchElementException.
   */
  def getFuture(actorPath: ActorPath, classifier: Classifier)(implicit classTag: ClassTag[classifier.OneOff], conf: ValidatorSuite): Future[classifier.OneOff] = {
    val message = JobActor.Get(classifier)
    val shortTimeout = Duration(2, "s")
    import conf.timeout
    conf.system.actorSelection(actorPath).resolveOne().flatMap(
      actor => ask(actor, message)(shortTimeout).mapTo[classifier.OneOff] recoverWith {
        case _: AskTimeoutException => Future.failed[classifier.OneOff](new NoSuchElementException)
      }
    )
  }

  /**enumerates the values of type T (according to the
   * `classifier`). This will spawn an anonymous actor which will
   * subscribe to the current JobActor (if available) and future
   * ones. This actor detects the new runs/JobActor by subscribing to
   * the system's EventStream. The JobActor sends () values when runs
   * are over, and depending on the value for `forever`, this
   * enumerator will either terminates (Input.EOF) or just emit a
   * Input.Empty.
   */
  def actorBasedEnumerator(classifier: Classifier, forever: Boolean)(
    implicit classTag: ClassTag[classifier.Streamed], vs: ValidatorSuite): Enumerator[Iterator[classifier.Streamed]] = {
    val (enumerator, channel) = Concurrent.broadcast[Iterator[classifier.Streamed]]
    val system = vs.system
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
        } catch {
          case t: Throwable =>
            logger.error("Enumerator exception", t)
            stop()
        }
      }

      def subscribeToJobActor(actorRef: ActorRef) {
        actorRef ! JobActor.Listen(classifier)
      }

      def subscribeToJobActor(actorPath: ActorPath) {
        import vs.timeout
        system.actorSelection(actorPath).resolveOne().map(actor => actor ! JobActor.Listen(classifier))
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
          case Running(_, actorName) => subscribeToJobActor(actorName.actorPath)
          // but if it's not running right now, then the status may
          // have changed. Let's give it a chance. Note that we
          // shouldn't be missing anything at this point as we've
          // already subscribed to the EventStream
          case _ => () /*Job.get(id).onSuccess { _.status match {
            case Running(_, jobActorPath) => subscribeToJobActor(system.actorFor(jobActorPath))
            case _ => ()
          }}*/
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
        case msg => logger.error("subscriber got " + msg); stop()
      }
    }
    // create (and start) the actor
    val actor = system.actorOf(Props(subscriberActor()))
    Concurrent.unicast(
      onStart = {
        channel => actor ! channel
      },
      onComplete = {
        actor ! PoisonPill
      },
      onError = {
        case (error, input) =>
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

  def runEvents(forever: Boolean = false)(implicit conf: ValidatorSuite): Enumerator[Iterator[RunEvent]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Enumerator()
      case Done(runId, _, _, _) => Run.enumerateRunEvents(runId)
      case Running(_, actorName) =>
        actorBasedEnumerator(Classifier.AllRunEvents, forever = forever)
    }
  }

  /**Enumerator for all the JobData-s, even for future runs.  This is
   * stateless.  If you just want the most up-to-date JobData, use
   * Job.jobData() instead. */
  def jobDatas(forever: Boolean = false)(implicit conf: ValidatorSuite): Enumerator[Iterator[JobData]] = {
    import conf._
    runDatas(forever) &> Enumeratee.map(_.map(runData => JobData(this, runData)))
  }

  /**returns the most up-to-date JobData for the Job, if available */
  def getJobData()(implicit conf: ValidatorSuite): Future[JobData] = {
    getRunData().map {
      JobData(this, _)
    }
  }

  /**this is stateless, so if you're the Done case, you want to use
   * Job.runData() instead */
  def runDatas(forever: Boolean = false)(implicit conf: ValidatorSuite): Enumerator[Iterator[RunData]] = {
    def enumerator = actorBasedEnumerator(Classifier.AllRunDatas, forever = forever)
    this.status match {
      case Done(_, _, _, runData) =>
        Enumerator(Iterator(runData)) andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  /**returns the most up-to-date RunData for the Job, if available */
  def getRunData()(implicit conf: ValidatorSuite): Future[RunData] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(RunData())
      case Done(_, _, _, runData) => Future.successful(runData)
      case Running(_, actorName) =>
        getFuture(actorName.actorPath, Classifier.AllRunDatas) recover {
          case _: NoSuchElementException => RunData()
        }
    }
  }

  def resourceDatas(forever: Boolean)(implicit conf: ValidatorSuite): Enumerator[Iterator[ResourceData]] = {
    def enumerator = actorBasedEnumerator(Classifier.AllResourceDatas, forever = forever)
    this.status match {
      case Done(runId, _, _, _) =>
        val partitionSize = 100
        val partitions: Future[Iterator[Iterator[ResourceData]]] =
          Run.getResourceDatas(runId).map(_.grouped(partitionSize).map(_.iterator))
        val current = Enumerator.flatten(partitions.map(Enumerator.enumerate(_))) //&> Enumeratee.mapM(x => x.map(_.iterator))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all ResourceDatas updates for url
  def resourceDatas(url: URL, forever: Boolean)(implicit conf: ValidatorSuite): Enumerator[ResourceData] = {
    def enumerator = actorBasedEnumerator(Classifier.ResourceDataFor(url), forever = forever) &> Enumeratee.mapConcat(_.toSeq)
    this.status match {
      case Done(runId, _, _, _) =>
        val current: Enumerator[ResourceData] =
          Enumerator(Run.getResourceDataForURL(runId, url)) &> Enumeratee.mapM(x => x)
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // the most up-to-date ResourceData for url
  def getResourceData(url: URL)(implicit conf: ValidatorSuite): Future[ResourceData] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.failed(new NoSuchElementException)
      case Done(runId, _, _, _) => Run.getResourceDataForURL(runId, url)
      case Running(_, actorName) =>
        getFuture(actorName.actorPath, Classifier.ResourceDataFor(url))
    }
  }

  // all current ResourceDatas
  def getResourceDatas()(implicit conf: ValidatorSuite): Future[Iterable[ResourceData]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getResourceDatas(runId)
      case Running(_, actorName) =>
        getFuture(actorName.actorPath, Classifier.AllResourceDatas)
    }
  }

  // all GroupedAssertionDatas updates
  def groupedAssertionDatas(forever: Boolean = false)(implicit conf: ValidatorSuite): Enumerator[Iterator[GroupedAssertionData]] = {
    def enumerator = actorBasedEnumerator(Classifier.AllGroupedAssertionDatas, forever = forever)
    this.status match {
      case Done(runId, _, _, _) =>
        val partitionSize = 100
        val partitions: Future[Iterator[Iterator[GroupedAssertionData]]] =
          Run.getGroupedAssertionDatas(runId).map(_.grouped(partitionSize).map(_.iterator))
        val current = Enumerator.flatten(partitions.map(Enumerator.enumerate(_)))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all current GroupedAssertionDatas
  def getGroupedAssertionDatas()(implicit conf: ValidatorSuite): Future[Iterable[GroupedAssertionData]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getGroupedAssertionDatas(runId)
      case Running(_, actorName) =>
        getFuture(actorName.actorPath, Classifier.AllGroupedAssertionDatas)
    }
  }

  // all Assertions updatesfor url
  def assertions(url: URL, forever: Boolean = false)(implicit conf: ValidatorSuite): Enumerator[Iterator[Assertion]] = {
    def enumerator = actorBasedEnumerator(Classifier.AssertionsFor(url), forever = forever)
    this.status match {
      case Done(runId, _, _, _) =>
        val partitionSize = 100
        val partitions: Future[Iterator[Iterator[Assertion]]] =
          Run.getAssertionsForURL(runId, url).map(_.grouped(partitionSize).map(_.iterator))
        val current = Enumerator.flatten(partitions.map(Enumerator.enumerate(_)))
        current andThen Enumerator.enumInput(Input.Empty) andThen enumerator
      case _ => enumerator
    }
  }

  // all current Assertions for `url`
  def getAssertions(url: URL)(implicit conf: ValidatorSuite): Future[Seq[Assertion]] = {
    import conf._
    this.status match {
      case NeverStarted | Zombie => Future.successful(Seq.empty)
      case Done(runId, _, _, _) => Run.getAssertionsForURL(runId, url)
      case Running(_, actorName) =>
        getFuture(actorName.actorPath, Classifier.AssertionsFor(url))
    }
  }

}

object Job {

  val logger = Logger.of(classOf[Job])

  def collection(implicit conf: Database): JSONCollection =
    conf.db("jobs")

  //  def sample(implicit conf: Database) = Job(
  //    JobId("50cb698f04ca20aa0283bc84"),
  //    "Sample report",
  //    DateTime.now(DateTimeZone.UTC),
  //    Strategy(
  //      entrypoint = URL("http://www.w3.org/"),
  //      linkCheck = false,
  //      maxResources = 10,
  //      filter = Filter(include = Everything, exclude = Nothing),
  //      assertorsConfiguration = AssertorsConfiguration.default),
  //    User.sample.id,
  //    NeverStarted,
  //    None)

  private def updateStatus(
      jobId: JobId,
      status: JobStatus,
      latestDoneOpt: Option[Done])(
    implicit conf: Database): Future[Unit] = {
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
    collection.update(selector, update, writeConcern = journalCommit) map {
      lastError =>
        if (!lastError.ok) throw lastError
    }

  }

  def updateStatus(
      jobId: JobId,
      status: JobStatus,
      latestDone: Done)(
    implicit conf: Database): Future[Unit] = {
    updateStatus(jobId, status, Some(latestDone))
  }

  def updateStatus(
      jobId: JobId,
      status: JobStatus)(
    implicit conf: Database): Future[Unit] = {
    updateStatus(jobId, status, None)
  }

  // returns the Job with the jobId and optionally the latest Run* for this Job
  // the Run may not exist if the Job was never started
  def get(jobId: JobId)(implicit conf: Database): Future[Job] = {
    val query = Json.obj("_id" -> toJson(jobId))
    collection.find(query).one[JsValue].map {
      case None => throw UnknownJob(jobId) //new NoSuchElementException("Invalid jobId: " + jobId)
      case Some(json) => json.as[Job]
    }
  }

  /**the list of all the Jobs */
  def getAll()(implicit conf: Database): Future[List[Job]] = {
    val cursor = collection.find(Json.obj()).cursor[JsValue]
    cursor.collect[List]() map {
      list => list flatMap { job =>
        try {
          Some(job.as[Job])
        } catch { case t: Throwable =>
          logger.error(s"could not deserialize ${job}")
          None
        }
      }
    }
  }

  def getCount()(implicit conf: Database): Future[Int] = {
    collection.db.command(Count("jobs", Some(BSONDocument())))
  }

  def getRunningJobs()(implicit conf: Database): Future[List[Job]] = {
    val query = Json.obj("status.actorName" -> Json.obj("$exists" -> JsBoolean(true)))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.collect[List]() map {
      list => list map {
        _.as[Job]
      }
    }
  }

  /**Resumes all the pending jobs (Running status) in the system.
   * The function itself is blocking and intended to be called when VS is (re-)started.
   * If resuming a Run fails (either an exception or a timeout) then the Job's status is updated to Zombie.
   */
  def resumeAllJobs()(implicit conf: ValidatorSuite): Unit = {
    import org.w3.vs.util.timer.FutureF
    val runningJobs = getRunningJobs().getOrFail()
    val duration = Duration("15s")
    runningJobs foreach {
      job =>
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

  def getFor(userId: UserId)(implicit conf: Database): Future[Iterable[Job]] = {
    import conf._
    val query = Json.obj("creator" -> toJson(userId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.collect[List]() map {
      list =>
        list map {
          json => json.as[Job]
        }
    }
  }

  /**returns the Job for this JobId, if it belongs to the provided user
   * if not, it throws an UnknownJob exception */
  /*def getFor(userId: UserId, jobId: JobId)(implicit conf: Database): Future[Job] = {
    val query = Json.obj("_id" -> toJson(jobId))
    val cursor = collection.find(query).cursor[JsValue]
    cursor.headOption map {
      case Some(json) if (json \ "creator").asOpt[UserId] === Some(userId) => json.as[Job]
      case _ => throw UnknownJob(jobId)
    }
  }*/

  // What is better in above implementation ?
  def getFor(jobId: JobId, user: Option[User])(implicit conf: Database): Future[Job] = {
    import scalaz.Equal
    // TODO: review. where to configure acls
    get(jobId).map{ job =>
      user match {
        case Some(user) if user.isRoot => job
        case Some(user) if job.isPublic => job
        case Some(user) if !user.owns(job) => throw AccessNotAllowed()
        case None if !job.isPublic => throw AccessNotAllowed()
        case _ => job
      }

    }
  }

  def save(job: Job)(implicit conf: Database): Future[Job] = {
    val jobJson = toJson(job)
    collection.insert(jobJson, writeConcern = journalCommit) map {
      lastError => job
    }
  }

  def delete(jobId: JobId)(implicit conf: Database): Future[Unit] = {
    val query = Json.obj("_id" -> toJson(jobId))
    collection.remove[JsValue](query) map {
      lastError => ()
    }
  }

  def reset(jobId: JobId, removeRunData: Boolean = true)(implicit conf: ValidatorSuite): Future[Unit] = {
    Job.get(jobId) flatMap {
      job =>
        job.cancel() // <- do not block!
        val rebornJob = job.copy(status = NeverStarted, latestDone = None)
        // as we don't change the jobId, this will override the previous one
        val update = collection.update(
          selector = Json.obj("_id" -> toJson(jobId)),
          update = toJson(rebornJob),
          writeConcern = journalCommit
        ) map {
          lastError => job
        }
        update flatMap {
          case job =>
            job.status match {
              case Done(runId, _, _, _) if removeRunData => Run.removeAll(runId)
              case Running(runId, _) if removeRunData => Run.removeAll(runId)
              case _ => Future.successful(())
            }
        }
    }
  }

}

