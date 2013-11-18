package org.w3.vs.actor

import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.util._
import org.w3.vs.web.Headers
import play.Logger
import org.w3.vs.web._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import com.codahale.metrics._
import scalaz.Equal
import scalaz.Scalaz._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
import akka.event._
import akka.actor.{ ActorSystem => AkkaActorSystem, _ }
import com.ning.http.client.{ Response, AsyncHttpClient, AsyncCompletionHandler }
import scalax.io._
import org.w3.vs.Metrics

object JobActor {

  /* actor events */
  case object Start
  case object Cancel
  case class Resume(actions: Iterable[RunAction])

  case class Get(classifier: Classifier)
  case class Listen(classifier: Classifier)
  case object Deafen

  val logger = Logger.of(classOf[JobActor])
//  val logger = new Object {
//    def debug(msg: String): Unit = println("== " + msg)
//    def error(msg: String): Unit = println("== " + msg)
//    def error(msg: String, t: Throwable): Unit = println("== " + msg)
//    def warn(msg: String): Unit = println("== " + msg)
//  }

  def saveEvent(event: RunEvent)(implicit vs: ValidatorSuite with Database): Future[Unit] = event match {
    case event@CreateRunEvent(userIdOpt, jobId, runId, actorName, strategy, timestamp) =>
      Run.saveEvent(event) flatMap { case () =>
        val running = Running(runId, actorName)
        Job.updateStatus(jobId, status = running)
      } flatMap { case () =>
        userIdOpt match {
          case Some(userId) => for {
            user <- User.get(userId)
            _ <- User.update(user.copy(credits = user.credits - strategy.maxResources)) map (_ => ())
          } yield ()
          case _ => Future.successful()
        }
      }

    case event@DoneRunEvent(userIdOpt, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) =>
      Run.saveEvent(event) flatMap { case () =>
        val done = Done(runId, doneReason, timestamp, RunData(resources, errors, warnings, JobDataIdle, Some(timestamp)))
        Job.updateStatus(jobId, status = done, latestDone = done)
      } flatMap { case () =>
        Job.get(jobId)
      } flatMap { job =>
        userIdOpt match {
          case Some(userId) => {
            for {
              user <- User.get(userId)
              _ <- User.update(user.copy(credits = user.credits + (job.strategy.maxResources - resources))) map (_ => ())
            } yield ()
          }
          case _ => Future.successful()
        }
      }

    case event@AssertorResponseEvent(userId, jobId, runId, ar, timestamp) =>
      Run.saveEvent(event)

    case event@ResourceResponseEvent(userId, jobId, runId, rr, timestamp) =>
      Run.saveEvent(event)

  }

}

import JobActor._

object JobActorState {
  implicit val equal = Equal.equalA[JobActorState]
}

sealed trait JobActorState
/** already started */
case object Started extends JobActorState
/** waiting for either Start or Resume event */
case object NotYetStarted extends JobActorState
/** stopping an Actor is an asynchronous operation, so we may have to deal with messages in the meantime */
case object Stopping extends JobActorState

// TODO extract all pure function in a companion object
class JobActor(job: Job, initialRun: Run)(implicit val conf: ValidatorSuite)
extends Actor with FSM[JobActorState, Run]
with EventBus
with ActorEventBus /* Represents an EventBus where the Subscriber type is ActorRef */
with ScanningClassification /* Maps Classifiers to Subscribers */ {

  type Classifier = org.w3.vs.actor.Classifier
  type Event = Any

  import JobActor.logger
  import conf._

  import job.{ creatorId => userId, id => jobId }
  import initialRun.runId

  val httpClient: AsyncHttpClient = Http.newAsyncHttpClient(job.strategy)

  /* some variables */

  var runEvents: Vector[RunEvent] = Vector.empty

  // TODO: change the way it's done here
  val assertionsActorRef = context.actorOf(Props(new AssertionsActor(job)), "assertions")

  /* functions */

  def executeActions(actions: Iterable[RunAction]): Unit = {
    actions foreach {
      case Fetch(url, method) =>
        logger.debug(s"${runId.shortId}: >>> ${method} ${url}")
        val boundRequestBuilder = method match {
          case GET => httpClient.prepareGet(url.httpClientFriendly).setHeader("Accept-Language", "en-us,en;q=0.5")
          case HEAD => httpClient.prepareHead(url.httpClientFriendly)
        }
        val timer = Metrics.crawler.time()
        Metrics.crawler.pending.inc()
        boundRequestBuilder.execute(new AsyncCompletionHandler[Unit]() {
          override def onCompleted(response: Response): Unit = {
            import java.util.{ Map => jMap, List => jList }
            import scala.collection.JavaConverters._
            val status = response.getStatusCode()
            val headers = Headers(response.getHeaders().asInstanceOf[jMap[String, jList[String]]])
            def resource = Resource.fromInputStream(response.getResponseBodyAsStream())
            val httpResponse = HttpResponse(url, method, status, headers, resource)
            self ! httpResponse
            timer.stop()
            Metrics.crawler.pending.dec()
//            cacheOpt foreach { _.save(httpResponse, resource) }
          }
          override def onThrowable(t: Throwable): Unit = {
            val errorResponse = ErrorResponse(url = url, method = method, why = t.getMessage)
            self ! errorResponse
            timer.stop()
            Metrics.crawler.failure()
            Metrics.crawler.pending.dec()
          }
        })
      case call: AssertorCall =>
        assertionsActorRef ! call
      case EmitEvent(event) =>
        self ! event
    }
  }

  def fireRightAway(sender: ActorRef, classifier: Classifier, run: Run, alwaysFireSomething: Boolean): Unit = {
    import Classifier._
    classifier match {
      case AllRunEvents =>
        sender ! runEvents
      case AllRunDatas => sender ! run.data
      case AllResourceDatas =>
        sender ! run.resourceDatas.values
      case ResourceDataFor(url) =>
        run.resourceDatas.get(url) match {
          case Some(resourceData) => sender ! resourceData
          case None =>
            if (alwaysFireSomething)
              sender ! Failure(new NoSuchElementException(url.toString))
            else ()
        }
      case AllAssertions =>
        val allAssertions: Iterable[Assertion] = run.allAssertions
        sender ! allAssertions
      case AssertionsFor(url) =>
        run.assertions.get(url) match {
          case Some(assertions) => sender ! assertions.values.flatten
          case None =>
            if (alwaysFireSomething)
              sender ! Failure(new NoSuchElementException(url.toString))
            else ()
        }
      case AllGroupedAssertionDatas =>
        val gad: Iterable[GroupedAssertionData] =
          run.groupedAssertionDatas.values
        sender ! gad
    }
  }

  // Computes the new state of this actor for a given RunEvent and executes all the side effects
  // (signature could be (State, RunEvent) => State)?
  def handleRunEvent(_run: Run, event: RunEvent): State = {

    runEvents :+= event

    // Get the new run and actions resulting from this this event
    // (The ResultStep class isn't really needed here)
    val ResultStep(run, actions) = _run.step(event)

    executeActions(actions)

    saveEvent(event)

    publish(run.data)

    publish(event)

    event match {
      case AssertorResponseEvent(_, _, _, AssertorResult(assertorId, sourceUrl, arAssertions), _) =>
        arAssertions.toIterable foreach { case (url, assertions) =>
          // ResourceData
          publish(run.resourceDatas(url))
          // Assertion
          run.assertions(url).values foreach { assertions =>
            assertions foreach { assertion =>
              publish(assertion)
            }
          }
          // GroupedAssertionData: we exploit here the fact that
          // these assertions are grouped by title (that's an
          // invariant enforced by FromHttpResponseAssertor) and
          // that this is coming from a single Assertor. That makes
          // it easy to find which AssertionTypeId-s were
          // impacted. There is basically exactly one for each
          // assertion.
          assertions foreach { assertion =>
            val assertionTypeId = AssertionTypeId(assertion)
            publish(run.groupedAssertionDatas(assertionTypeId))
          }
        }
      case _ => ()
    }

    // compute the next step and do side-effects
    val state = event match {
      case CreateRunEvent(_, _, _, _, _, _) =>
        logger.info(s"id=${job.id} status=started user-id=${job.creatorId.getOrElse("None")} url=${job.strategy.entrypoint} max=${job.strategy.maxResources}")
        val running = Running(run.runId, RunningActorName(self.path.name))
        // Job.run() is waiting for this value
        val from = sender
        from ! running
        goto(Started) using run
      case DoneRunEvent(_, jobId, _, doneReason, resources, errors, warnings, _, _, _) =>
        doneReason match {
          case Completed => logger.info(s"""id=${job.id} status=completed result="Resources: ${resources} Errors: ${errors} Warnings: ${warnings}" """)
          case Cancelled => logger.info(s"""id=${job.id} status=canceled result="Resources: ${resources} Errors: ${errors} Warnings: ${warnings}" """)
        }
        Metrics.jobs.errors(errors)
        Metrics.jobs.warnings(warnings)
        Metrics.jobs.resources(resources)
        stopThisActor()
        goto(Stopping) using run
      case _ =>
        stay() using run
    }

    state
  }

  /* state machine */

  startWith(NotYetStarted, initialRun)

  whenUnhandled {

    case Event(Get(classifier), run) =>
      fireRightAway(sender, classifier, run, alwaysFireSomething = true)
      stay()

    case Event(Listen(classifier), run) =>
      val from = sender
      if (subscribe(from, classifier)) {
        context.watch(from)
        fireRightAway(from, classifier, run, alwaysFireSomething = false)
      }
      stay()

    case Event(Terminated(ref), run) =>
      unsubscribe(ref)
      stay()

    case Event(Deafen, run) =>
      unsubscribe(sender)
      stay()

    case Event(e, run) =>
      logger.warn(s"""status=unexpected message="Unexpected event for run: ${run.shortId} - ${e.getClass()}" """)
      stay()

  }

  when(Stopping) {

    // we still answer Listen requests in the Stopping state, but we
    // just send the termination message right away. There is no need
    // to subscribe anybody.
    case Event(Listen(classifier), run) =>
      val from = sender
      fireRightAway(from, classifier, run, alwaysFireSomething = false)
      from ! ()
      stay()

//    case Event(e, run) =>
//      println(s"${run.shortId}: received ${e} while Stopping")
//      stay()

  }

  when(NotYetStarted) {

    case Event(Start, run) =>
      val event = CreateRunEvent(userId, jobId, run.runId, RunningActorName(self.path.name), job.strategy, job.createdOn)
      handleRunEvent(run, event)

    case Event(Resume(actions), run) =>
      sender ! ()
      executeActions(actions)
      goto(Started)

  }

  when(Started) {

    case Event(result: AssertorResult, run) =>
      logger.debug(s"${run.shortId}: ${result.assertor} produced AssertorResult for ${result.sourceUrl}")
      val fixedAssertorResult = run.fixedAssertorResult(result)
      val event = AssertorResponseEvent(userId, jobId, run.runId, fixedAssertorResult)
      handleRunEvent(run, event)

    case Event(failure: AssertorFailure, run) =>
      val event = AssertorResponseEvent(userId, jobId, run.runId, failure)
      handleRunEvent(run, event)

    case Event(httpResponse: HttpResponse, run) =>
      // logging/monitoring
      logger.debug(s"${run.shortId}: <<< ${httpResponse.url}")
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, httpResponse)
      handleRunEvent(run, event)

    case Event(error: ErrorResponse, run) =>
      // logging/monitoring
      logger.debug(s"""${run.shortId}: <<< error when fetching ${error.url} because ${error.why}""")
      // logic
      val event = ResourceResponseEvent(userId, jobId, run.runId, error)
      handleRunEvent(run, event)

    case Event(Cancel, run) =>
      // logging/monitoring
      logger.debug(s"${run.shortId}: Cancel")
      // logic
      val event = DoneRunEvent(userId, jobId, run.runId, Cancelled, run.data.resources, run.data.errors, run.data.warnings, run.resourceDatas, run.groupedAssertionDatas.values)
      handleRunEvent(run, event)

    case Event(event: DoneRunEvent, run) =>
      handleRunEvent(run, event)

  }

  def stopThisActor(): Unit = {

    val it = subscribers.iterator()
    while(it.hasNext) {
      val subscriber: ActorRef = it.next()._2
      subscriber ! ()
    }

    // we stay a bit longer in the Stopping state. This lets the
    // occasion for pending Enumerators to catch up
    val duration = scala.concurrent.duration.Duration(5, "s")
    system.scheduler.scheduleOnce(duration, self, PoisonPill)
  }

  var timer: Timer.Context = _

  override def preStart(): Unit = {
    Metrics.jobs.running.inc()
    timer = Metrics.jobs.time()
  }

  override def postStop(): Unit = {
    Metrics.jobs.running.dec()
    timer.stop()
    httpClient.close()
  }

  /* EventBus */

  protected def compareClassifiers(a: Classifier, b: Classifier): Int =
    Classifier.ordering.compare(a, b)

  protected def matches(classifier: Classifier, event: Any): Boolean =
    classifier.matches(event)

  protected def publish(event: Event, subscriber: ActorRef): Unit =
    subscriber ! event

}
