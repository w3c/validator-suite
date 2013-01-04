package org.w3.vs.model

import java.nio.channels.ClosedChannelException
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.akkaext._
import org.w3.vs.actor.message._
import akka.actor._
import play.api.libs.iteratee._
import play.Logger
import org.w3.util._
import scalaz.Equal
import scalaz.Equal._
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import scala.concurrent.{ ops => _, _ }
import scala.concurrent.ExecutionContext.Implicits.global

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
  strategy: Strategy,
  creatorId: UserId) {

  import Job.logger

//  @deprecated("", "")
  def getRun()(implicit conf: VSConfiguration): Future[Run] = {
    import conf._
    (PathAware(usersRef, path) ? GetRun).mapTo[Run]
  }

  def waitLastWrite()(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val wait = (PathAware(usersRef, path) ? WaitLastWrite).mapTo[Future[Unit]]
    wait.flatMap(x => x)
  }

  def getAssertions()(implicit conf: VSConfiguration): Future[Iterable[Assertion]] = {
    getRun() map {
      run => run.assertions.toIterable
    }
  }

  def getActivity()(implicit conf: VSConfiguration): Future[RunActivity] = {
    getRun().map(_.activity)
  }

  def getData()(implicit conf: VSConfiguration): Future[JobData] = {
    getRun().map(_.jobData)
  }


  // Get all runVos for this job, group by id, and for each runId take the latest completed jobData if any
  def getHistory(): Future[Iterable[JobData]] = {
    sys.error("")
  }

  def getCompletedOn()(implicit conf: VSConfiguration): Future[Option[DateTime]] = {
    Job.getLastCompleted(id)
  }
  
  def save()(implicit conf: VSConfiguration): Future[Job] =
    Job.save(this)
  
  def delete()(implicit conf: VSConfiguration): Future[Unit] = {
    cancel()
    Job.delete(id)
  }
  
  def run()(implicit conf: VSConfiguration): Future[(UserId, JobId, RunId)] = {
    import conf._
    (PathAware(usersRef, path) ? Refresh).mapTo[(UserId, JobId, RunId)]
  }
  
  def cancel()(implicit conf: VSConfiguration): Unit = 
    PathAware(usersRef, path) ! Stop

  def on()(implicit conf: VSConfiguration): Unit = 
    PathAware(usersRef, path) ! BeProactive

  def off()(implicit conf: VSConfiguration): Unit = 
    PathAware(usersRef, path) ! BeLazy

  def resume()(implicit conf: VSConfiguration): Unit = 
    PathAware(usersRef, path) ! Resume

  def getSnapshot()(implicit conf: VSConfiguration): Future[JobData] = {
    import conf._
    (PathAware(usersRef, path) ? GetSnapshot).mapTo[JobData]
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
    listen(subscriber)
    _enumerator
  }

  def listen(listener: ActorRef)(implicit conf: VSConfiguration): Unit =
    PathAware(usersRef, path).tell(Listen(listener), listener)
  
  def deafen(listener: ActorRef)(implicit conf: VSConfiguration): Unit =
    PathAware(usersRef, path).tell(Deafen(listener), listener)
  
  private def usersRef(implicit conf: VSConfiguration) = {
    import conf._
    system.actorFor(system / "users")
  }

  private def path(implicit conf: VSConfiguration): ActorPath = {
    import conf._
    system / "users" / creatorId.id / "jobs" / id.id
  }
  
  def !(message: Any)(implicit sender: ActorRef = null, conf: VSConfiguration): Unit =
    PathAware(usersRef, path) ! message

}

object Job {

  def createNewJob(name: String, strategy: Strategy, creatorId: UserId): Job =
    Job(JobId(), name, DateTime.now(DateTimeZone.UTC), strategy, creatorId)

  val logger = Logger.of(classOf[Job])

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("jobs")

  def sample(implicit conf: VSConfiguration) = Job.apply(
    id = JobId("50cb698f04ca20aa0283bc84"),
    name = "Sample report",
    createdOn = DateTime.now(DateTimeZone.UTC),
    strategy = Strategy(
      entrypoint = URL("http://www.w3.org/"),
      linkCheck = false,
      maxResources = 10,
      filter = Filter(include = Everything, exclude = Nothing),
      assertorsConfiguration = AssertorsConfiguration.default),
    creatorId = User.sample.id
  )

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

  def getFor(userId: UserId)(implicit conf: VSConfiguration): Future[Iterable[Job]] = {
    import conf._
    val query = Json.obj("creator" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { list =>
      list map { json => json.as[Job] }
    }
  }

  // TODO
  // indexes: event && runId
  def getLastRun(jobId: JobId)(implicit conf: VSConfiguration): Future[Option[(Run, Iterable[URL], Iterable[AssertorCall])]] = {
    import conf._
    // we first look for the latest runId for the given jobId
    val lastCreatedRunQuery = QueryBuilder().
      query( Json.obj(
        "jobId" -> toJson(jobId),
        "event" -> toJson("create-run")) ).
      sort( "createdAt" -> SortOrder.Descending ).
      projection( BSONDocument(
        "runId" -> BSONInteger(1),
        "_id" -> BSONInteger(0)) )

    Run.collection.find[JsValue](lastCreatedRunQuery).headOption flatMap {
      case None => Future.successful(None)
      case Some(json) =>
        val runId = (json \ "runId").as[RunId]
        Run.get(runId) map Some.apply
    }
  }

  def getLastCompleted(jobId: JobId)(implicit conf: VSConfiguration): Future[Option[DateTime]] = {
    import conf._
    val query= QueryBuilder().
      query( Json.obj(
        "jobId" -> toJson(jobId),
        "event" -> toJson("complete-run")) ).
      sort("at" -> SortOrder.Descending).
      projection( BSONDocument(
        "at" -> BSONInteger(1),
        "_id" -> BSONInteger(0)) )
    val cursor = Run.collection.find[JsValue](query)
    cursor.headOption map { jsonOpt =>
      jsonOpt.flatMap(json => (json \ "at").as[Option[DateTime]])
    }
  }

  def save(job: Job)(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    val jobJson = toJson(job)
    collection.insert(jobJson) map { lastError => job }
  }

  def delete(jobId: JobId)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val query = Json.obj("_id" -> toJson(jobId))
    collection.remove[JsValue](query) map { lastError => () }
  }

}

