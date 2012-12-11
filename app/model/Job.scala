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

case class Job(id: JobId, vo: JobVO)(implicit conf: VSConfiguration) {

  import conf._

  val creatorId = vo.creator

  private val logger = Logger.of(classOf[Job])
  
  def getCreator(): Future[User] =
    User.get(creatorId)

  def getRun(): Future[Run] = {
    (PathAware(usersRef, path) ? GetRun).mapTo[Run]
  }

  def waitLastWrite(): Future[Unit] = {
    val wait = (PathAware(usersRef, path) ? WaitLastWrite).mapTo[Future[Unit]]
    wait.flatMap(x => x)
  }

  def getAssertions(): Future[Iterable[Assertion]] = {
    getRun() map {
      run => run.assertions.toIterable
    }
  }

  def getActivity(): Future[RunActivity] = {
    getRun().map(_.activity)
  }

  def getData(): Future[JobData] = {
    getRun().map(_.jobData)
  }


  // Get all runVos for this job, group by id, and for each runId take the latest completed jobData if any
  def getHistory(): Future[Iterable[JobData]] = {
    sys.error("")
  }

  def getCompletedOn(): Future[Option[DateTime]] = {
    Job.getLastCompleted(id)
  }
  
  def save(): Future[Job] = Job.save(this)
  
  def delete(): Future[Unit] = {
    cancel()
    Job.delete(id)
  }
  
  def run(): Future[(UserId, JobId, RunId)] =
    (PathAware(usersRef, path) ? Refresh).mapTo[(UserId, JobId, RunId)]
  
  def cancel(): Unit = 
    PathAware(usersRef, path) ! Stop

  def on(): Unit = 
    PathAware(usersRef, path) ! BeProactive

  def off(): Unit = 
    PathAware(usersRef, path) ! BeLazy

  def resume(): Unit = 
    PathAware(usersRef, path) ! Resume

  def getSnapshot(): Future[JobData] =
    (PathAware(usersRef, path) ? GetSnapshot).mapTo[JobData]

  lazy val enumerator: Enumerator[RunUpdate] = {
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

  def listen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Listen(listener), listener)
  
  def deafen(implicit listener: ActorRef): Unit =
    PathAware(usersRef, path).tell(Deafen(listener), listener)
  
  private val usersRef = system.actorFor(system / "users")

  private val path: ActorPath = {
    system / "users" / vo.creator.id / "jobs" / id.id
  }
  
  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    PathAware(usersRef, path) ! message

}

object Job {

  def collection(implicit conf: VSConfiguration): DefaultCollection =
    conf.db("jobs")

  def apply(
    id: JobId = JobId(),
    name: String,
    createdOn: DateTime = DateTime.now(DateTimeZone.UTC),
    strategy: Strategy,
    creator: UserId)(
    implicit conf: VSConfiguration): Job =
      Job(id, JobVO(name, createdOn, strategy, creator))

  implicit def toVO(job: Job): JobVO = job.vo

  // returns the Job with the jobId and optionally the latest Run* for this Job
  // the Run may not exist if the Job was never started
  def get(jobId: JobId)(implicit conf: VSConfiguration): Future[Job] = {
    val query = Json.obj("_id" -> toJson(jobId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { list =>
      val json: JsValue = list.headOption match {
        case Some(json) => json
        case _ => throw new NoSuchElementException("Invalid jobId: " + jobId)
      }
      val jobId_ = (json \ "_id").as[JobId] // Is that necessary ? Why would it differ from the jobId parameter ? Why not an assert ?
      val jobVo = json.as[JobVO]
      Job(jobId_, jobVo)
    }
  }

  def getFor(userId: UserId)(implicit conf: VSConfiguration): Future[Iterable[Job]] = {
    import conf._
    val query = Json.obj("creator" -> toJson(userId))
    val cursor = collection.find[JsValue, JsValue](query)
    cursor.toList map { list => list map { json =>
      val jobId = (json \ "_id").as[JobId]
      val jobVo = json.as[JobVO]
      Job(jobId, jobVo)
    }}
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

    Run.collection.find[JsValue](lastCreatedRunQuery).toList flatMap { list =>
      list.headOption match {
        case None => Future.successful(None)
        case Some(json) => 
          val runId = (json \ "runId").as[RunId]
          Run.get(runId) map Some.apply
      }
    }
  }

  def getLastCompletedRun(jobId: JobId)(implicit conf: VSConfiguration): Future[Option[(Run, Iterable[URL], Iterable[AssertorCall])]] = {
    import conf._

    val query = QueryBuilder().
      query( Json.obj(
        "jobId" -> toJson(jobId),
        "event" -> toJson("create-event"),
        "completedOn" -> Json.obj("$exists" -> JsBoolean(true))) ).
      sort( "completedOn" -> SortOrder.Descending ).
      projection( BSONDocument(
        "runId" -> BSONInteger(1),
        "_id" -> BSONInteger(0)) )

    Run.collection.find[JsValue](query).toList flatMap { list =>
      list.headOption match {
        case None => Future.successful(None)
        case Some(json) => 
          val runId = json.as[RunId]
          Run.get(runId) map Some.apply
      }
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
    cursor.toList map { list =>
      list.headOption.flatMap(json => (json \ "at").as[Option[DateTime]])
    }
  }

  def save(job: Job)(implicit conf: VSConfiguration): Future[Job] = {
    import conf._
    val jobJson = toJson(job.vo).asInstanceOf[JsObject] + ("_id" -> toJson(job.id))
    collection.insert(jobJson) map { lastError => job }
  }

  def delete(jobId: JobId)(implicit conf: VSConfiguration): Future[Unit] = {
    import conf._
    val query = Json.obj("_id" -> toJson(jobId))
    collection.remove[JsValue](query) map { lastError => () }
  }

}

