package org.w3.vs.store

import org.w3.vs.model._
import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.util.{ Headers, URL }
import org.w3.vs._
import org.w3.vs.actor.JobActor._
import org.w3.vs.actor.AssertorCall
import scala.util._

// Reactive Mongo imports
import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._
// Reactive Mongo plugin
import play.modules.reactivemongo._
import play.modules.reactivemongo.PlayBsonImplicits._
// Play Json imports
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson


object Formats {

  def reeads[T](body: => T): JsResult[T] = try {
    JsSuccess(body)
  } catch {
    case e: Exception => JsError(e.getMessage)
  }

  implicit def tuple2Format[A, B](implicit aFormat: Format[A], bFormat: Format[B]) = new Format[(A, B)] {
    def reads(json: JsValue): JsResult[(A, B)] = json match {
      case JsArray(Seq(a, b)) => JsSuccess((a.as[A], b.as[B]))
      case _ => JsError("tuple2Format")
    }
    def writes(t: (A, B)) = JsArray(Seq(toJson(t._1), toJson(t._2)))    
  }

  implicit def tuple3Format[A, B, C](implicit aFormat: Format[A], bFormat: Format[B], cFormat: Format[C]) = new Format[(A, B, C)] {
    def reads(json: JsValue): JsResult[(A, B, C)] = json match {
      case JsArray(Seq(a, b, c)) => JsSuccess((a.as[A], b.as[B], c.as[C]))
      case _ => JsError("tuple3Format")
    }
    def writes(t: (A, B, C)) = JsArray(Seq(toJson(t._1), toJson(t._2), toJson(t._3)))    
  }

  implicit def mapFormat[A, B](implicit aFormat: Format[A], bFormat: Format[B]) = new Format[Map[A, B]] {
    implicit val f = implicitly[Format[List[(A, B)]]]
    def reads(json: JsValue): JsResult[Map[A, B]] = f.reads(json).map(_.toMap)
    def writes(map: Map[A, B]) = f.writes(map.toList)
  }

  def constant[T](stringConst: String, const: T): Format[T] = new Format[T] {
    def reads(json: JsValue): JsResult[T] = json match {
      case JsString(`stringConst`) => JsSuccess(const)
      case _ => JsError(stringConst)
    }
    def writes(t: T): JsValue = JsString(stringConst)
  }

  def string[T](apply: String => T, unapply: T => String): Format[T] = new Format[T] {
    def reads(json: JsValue): JsResult[T] = json match {
      case JsString(s) => JsSuccess(apply(s))
      case _ => JsError()
    }
    def writes(t: T): JsValue = JsString(unapply(t))
  }

  def oid[T <: Id](apply: BSONObjectID => T): Format[T] = new Format[T] {
    def reads(json: JsValue): JsResult[T] = {
      val oid = (json \ "$oid").as[String]
      JsSuccess(apply(BSONObjectID(oid)))
    }
    def writes(id: T): JsValue = Json.obj("$oid" -> id.oid.stringify)
  }

  // it uses the UTC timezone
  // there is a pending ticket in Play to deal with that properly
  // with the default Format[DateTime]
  implicit object jodaTimeUTCFormat extends Format[DateTime] {
    def reads(json: JsValue): JsResult[DateTime] = json match {
      case JsNumber(d) => JsSuccess(new DateTime(d.toLong, DateTimeZone.UTC))
      case _ => JsError("jodatime")
    }
    def writes(d: DateTime): JsValue = JsNumber(d.getMillis)
  }

  /***********/

  implicit val BeProactiveFormat = constant[BeProactive.type]("beproactive", BeProactive)

  implicit val BeLazyFormat = constant[BeLazy.type]("belazy", BeLazy)

  implicit val URLFormat = string[URL](URL(_), _.underlying.toString)

  implicit val AssertorIdFormat = string[AssertorId](AssertorId(_), _.id)

  implicit val UserIdFormat = oid[UserId](UserId(_))

  implicit val JobIdFormat = oid[JobId](JobId(_))

  implicit val RunIdFormat = oid[RunId](RunId(_))

  implicit val FilterFormat = constant[Filter]("includeEverything", Filter.includeEverything)

  implicit val StrategyFormat: Format[Strategy] = (
    (__ \ 'entrypoint).format[URL] and
    (__ \ 'linkCheck).format[Boolean] and
    (__ \ 'maxResources).format[Int] and
    (__ \ 'filter).format[Filter] and
    (__ \ 'assertorsConfiguration).format[AssertorsConfiguration]
  )(Strategy.apply _, unlift(Strategy.unapply _))

  implicit val JobVOFormat: Format[JobVO] = (
    (__ \ 'name).format[String] and
    (__ \ 'createdOn).format[DateTime] and
    (__ \ 'strategy).format[Strategy] and
    (__ \ 'creator).format[UserId]
  )(JobVO.apply _, unlift(JobVO.unapply _))
  
  implicit val ContextFormat: Format[Context] = (
    (__ \ 'content).format[String] and
    (__ \ 'line).format[Option[Int]] and
    (__ \ 'column).format[Option[Int]]
  )(Context.apply _, unlift(Context.unapply _))

  val ErrorFormat = constant[Error.type]("error", Error)
  val WarningFormat = constant[Warning.type]("warning", Warning)
  val InfoFormat = constant[Info.type]("info", Info)
  implicit object AssertionSeverityFormat extends Format[AssertionSeverity] {
    def reads(json: JsValue): JsResult[AssertionSeverity] =
      ErrorFormat.reads(json) orElse WarningFormat.reads(json) orElse InfoFormat.reads(json)
    def writes(severity: AssertionSeverity) = severity match {
      case Error => ErrorFormat.writes(Error)
      case Warning => WarningFormat.writes(Warning)
      case Info => InfoFormat.writes(Info)
    }
  }

  implicit val AssertionFormat: Format[Assertion] = (
    (__ \ 'url).format[URL] and
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'contexts).format[List[Context]] and
    (__ \ 'lang).format[String] and
    (__ \ 'title).format[String] and
    (__ \ 'severity).format[AssertionSeverity] and
    (__ \ 'description).format[Option[String]] and
    (__ \ 'timestamp).format[DateTime]
  )(Assertion.apply _, unlift(Assertion.unapply _))

  val GETFormat = constant[GET.type]("GET", GET)
  val HEADFormat = constant[HEAD.type]("HEAD", HEAD)
  implicit object HttpMethodFormat extends Format[HttpMethod] {
    def reads(json: JsValue): JsResult[HttpMethod] =
      GETFormat.reads(json) orElse HEADFormat.reads(json)
    def writes(method: HttpMethod) = method match {
      case GET => GETFormat.writes(GET)
      case HEAD => HEADFormat.writes(HEAD)
    }
  }

  val ErrorResponseFormat: Format[ErrorResponse] = (
    (__ \ 'url).format[URL] and
    (__ \ 'method).format[HttpMethod] and
    (__ \ 'why).format[String]
  )(ErrorResponse.apply _, unlift(ErrorResponse.unapply _))

  val HttpResponseFormat: Format[HttpResponse] = (
    (__ \ 'url).format[URL] and
    (__ \ 'method).format[HttpMethod] and
    (__ \ 'status).format[Int] and
    (__ \ 'headers).format[Headers] and
    (__ \ 'extractedURLs).format[List[URL]]
  )(HttpResponse.apply _, unlift(HttpResponse.unapply _))

  implicit object ResourceResponseFormat extends Format[ResourceResponse] {
    def reads(json: JsValue): JsResult[ResourceResponse] =
      ErrorResponseFormat.reads(json) orElse HttpResponseFormat.reads(json)
    def writes(rr: ResourceResponse) = rr match {
      case er@ErrorResponse(_, _, _) => ErrorResponseFormat.writes(er)
      case hr@HttpResponse(_, _, _, _, _) => HttpResponseFormat.writes(hr)
    }
  }

  val AssertorFailureFormat: Format[AssertorFailure] = (
    (__ \ 'context).format[(UserId, JobId, RunId)] and
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'sourceUrl).format[URL] and
    (__ \ 'why).format[String]
  )(AssertorFailure.apply _, unlift(AssertorFailure.unapply _))

  val AssertorResultFormat: Format[AssertorResult] = (
    (__ \ 'context).format[(UserId, JobId, RunId)] and
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'sourceUrl).format[URL] and
    (__ \ 'assertions).format[List[Assertion]]
  )(AssertorResult.apply _, unlift(AssertorResult.unapply _))

  implicit object AssertorResponseFormat extends Format[AssertorResponse] {
    def reads(json: JsValue): JsResult[AssertorResponse] =
      AssertorResultFormat.reads(json) orElse AssertorFailureFormat.reads(json)
    def writes(ar: AssertorResponse) = ar match {
      case result@AssertorResult(_, _, _, _) => AssertorResultFormat.writes(result)
      case failure@AssertorFailure(_, _, _, _) => AssertorFailureFormat.writes(failure)
    }
  }

  val AssertorResponseEventFormat: Format[AssertorResponseEvent] = (
    (__ \ 'event).format[String] and
    (__ \ 'ar).format[AssertorResponse] and
    (__ \ 'timestamp).format[DateTime]
  )({ case (_, timestamp, ar) => AssertorResponseEvent(timestamp, ar) }, { case AssertorResponseEvent(timestamp, ar) => ("assertor-response", timestamp, ar) } )

  val ResourceResponseEventFormat: Format[ResourceResponseEvent] = (
    (__ \ 'event).format[String] and
    (__ \ 'rr).format[ResourceResponse] and
    (__ \ 'timestamp).format[DateTime]
  )({ case (_, timestamp, rr) => ResourceResponseEvent(timestamp, rr) }, { case ResourceResponseEvent(timestamp, ar) => ("resource-response", timestamp, ar) })

  val BeXEventFormat: Format[BeXEvent] = (
    (__ \ 'event).format[String] and
    (__ \ 'timestamp).format[DateTime]
  )({
    case (event, timestamp) if event == "be-proactive" => BeProactiveEvent(timestamp)
    case (event, timestamp) if event == "be-lazy" => BeLazyEvent(timestamp)
  }, {
    case BeProactiveEvent(timestamp) => ("be-proactive", timestamp)
    case BeLazyEvent(timestamp) => ("be-lazy", timestamp)
  })

  implicit object RunEventFormat extends Format[RunEvent] {
    def reads(json: JsValue): JsResult[RunEvent] =
      AssertorResponseEventFormat.reads(json) orElse ResourceResponseEventFormat.reads(json) orElse BeXEventFormat.reads(json)
    def writes(event: RunEvent) = event match {
      case e@AssertorResponseEvent(_, _) => AssertorResponseEventFormat.writes(e)
      case e@ResourceResponseEvent(_, _) => ResourceResponseEventFormat.writes(e)
      case e@BeProactiveEvent(_) => BeXEventFormat.writes(e)
      case e@BeLazyEvent(_) => BeXEventFormat.writes(e)
    }
  }

  implicit val UserVOFormat: Format[UserVO] = (
    (__ \ 'name).format[String] and
    (__ \ 'email).format[String] and
    (__ \ 'password).format[String] and
    (__ \ 'isSubscriber).format[Boolean]
  )(UserVO.apply _, unlift(UserVO.unapply _))

  implicit object RunReads extends Reads[(Run, Iterable[URL], Iterable[AssertorCall])] {
    import org.w3.util.DateTimeOrdering
    def reads(json: JsValue): JsResult[(Run, Iterable[URL], Iterable[AssertorCall])] = reeads {
      val runId = (json \ "_id").as[RunId]
      val userId = (json \ "userId").as[UserId]
      val jobId = (json \ "jobId").as[JobId]
      val strategy = (json \ "strategy").as[Strategy]
      val createdAt = (json \ "createdAt").as[DateTime]
      val completedOn = (json \ "completedOn").as[Option[DateTime]]
      val events = (json \ "events").as[List[RunEvent]]
      val start = System.currentTimeMillis()
      var toBeFetched = Set.empty[URL]
      var toBeAsserted = Map.empty[(URL, AssertorId), AssertorCall]
      val (initialRun, urls) = Run((userId, jobId, runId), strategy, createdAt).newlyStartedRun
      var run = initialRun
      toBeFetched ++= urls
      completedOn foreach { at => run = run.completeOn(at) }
      events.toList.sortBy(_.timestamp) foreach {
        case AssertorResponseEvent(ar@AssertorResult(_, assertor, url, _), _) => {
          toBeAsserted -= ((url, assertor))
          run = run.withAssertorResult(ar)
        }
        case AssertorResponseEvent(af@AssertorFailure(_, assertor, url, _), _) => {
          toBeAsserted -= ((url, assertor))
          run = run.withAssertorFailure(af)
        }
        case ResourceResponseEvent(hr@HttpResponse(url, _, _, _, _), _) => {
          toBeFetched -= url
          val (newRun, urls, assertorCalls) = run.withHttpResponse(hr)
          run = newRun
          toBeFetched ++= urls
          assertorCalls foreach { ac =>
            toBeAsserted += ((ac.response.url, ac.assertor.id) -> ac)
          }
        }
        case ResourceResponseEvent(er@ErrorResponse(url, _, _), _) => {
          toBeFetched -= url
          val (newRun, urls) = run.withErrorResponse(er)
          run = newRun
          toBeFetched ++= urls
        }
        case BeProactiveEvent(_) => ()
        case BeLazyEvent(_) => ()
      }
      val result = (run, toBeFetched, toBeAsserted.values)
      val end = System.currentTimeMillis()
      logger.debug("Run deserialized in %dms (found %d events)" format (end - start, events.size))
      result
    }
  }

  implicit object RunWrites extends Writes[Run] {
    def writes(run: Run) = {
      Json.obj(
        "_id" -> toJson(run.runId),
        "userId" -> toJson(run.userId),
        "jobId" -> toJson(run.jobId),
        "strategy" -> toJson(run.strategy),
        "createdAt" -> toJson(run.createdAt),
        "events" -> Json.arr()
      )
    }
  }

}
