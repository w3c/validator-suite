package org.w3.vs.store

import org.joda.time.{ DateTime, DateTimeZone }
import org.w3.vs.web._
import org.w3.vs.model._

// Reactive Mongo imports
import reactivemongo.bson._
// Play Json imports
import play.api.libs.json._
import play.api.libs.functional.syntax._
import Json.toJson
import play.api.libs.json.Reads.pattern

object Formats {

  implicit def IteratorWrites[A: Writes] = new Writes[Iterator[A]] {
    def writes(as: Iterator[A]) = JsArray(as.map(toJson(_)).toSeq)
  }

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

  implicit def mapFormat[A, B](implicit aFormat: StringLikeFormat[A], bFormat: Format[B]) = new Format[Map[A, B]] {
    implicit val owrites = implicitly[OWrites[Map[String, B]]] // TODO
    implicit val f = implicitly[Format[Map[String, B]]]
    def reads(json: JsValue): JsResult[Map[A, B]] = f.reads(json).map(_.map{ case (k, v) => (aFormat.apply(k), v) })
    def writes(m: Map[A, B]) = f.writes(m.map{ case (k, v) => (aFormat.unapply(k), v) })
  }

  def constant[T](stringConst: String, const: T): Format[T] = new Format[T] {
    def reads(json: JsValue): JsResult[T] = json match {
      case JsString(`stringConst`) => JsSuccess(const)
      case _ => JsError(stringConst)
    }
    def writes(t: T): JsValue = JsString(stringConst)
  }

  class StringLikeFormat[T](val apply: String => T, val unapply: T => String) extends Format[T] {
    def reads(json: JsValue): JsResult[T] = json match {
      case JsString(s) => JsSuccess(apply(s))
      case _ => JsError(Json.prettyPrint(json) + " is not a JsString")
    }
    def writes(t: T): JsValue = JsString(unapply(t))
  }

  def string[T](apply: String => T, unapply: T => String): StringLikeFormat[T] =
    new StringLikeFormat[T](apply, unapply)

  implicit val StringLikeString = string[String](s => s, s => s)

  def oid[T <: Id](apply: BSONObjectID => T): Format[T] = new Format[T] {
    def reads(json: JsValue): JsResult[T] = {
      val oidOpt = (json \ "$oid").asOpt[String]
      oidOpt.map{ oid =>
        JsSuccess(apply(BSONObjectID(oid)))
      }.getOrElse {
        JsError("couldn't parse the id")
      }
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

  implicit val URLFormat = string[URL](URL(_), _.underlying.toString)

  implicit val AssertorIdFormat = string[AssertorId](AssertorId(_), _.id)

  implicit val UserIdFormat = oid[UserId](UserId(_))

  implicit val CouponIdFormat = oid[CouponId](CouponId(_))

  implicit val JobIdFormat = oid[JobId](JobId(_))

  implicit val RunIdFormat = oid[RunId](RunId(_))

  implicit val FilterFormat = constant[Filter]("includeEverything", Filter.includeEverything)

  implicit val StrategyFormat: Format[Strategy] = (
    (__ \ 'entrypoint).format[URL] and
    (__ \ 'maxResources).format[Int]
  )(Strategy.apply, unlift(Strategy.unapply))

  import akka.actor.ActorPath
  implicit val RunningActorNameFormat = string[RunningActorName](RunningActorName.apply, _.name)

  implicit val ResourceDataFormat: Format[ResourceData] = (
    (__ \ 'url).format[URL] and
    (__ \ 'lastValidated).format[DateTime] and
    (__ \ 'warnings).format[Int] and
    (__ \ 'errors).format[Int]
  )(ResourceData.apply, unlift(ResourceData.unapply))

 
  implicit val JobDataRunningFormat: Format[JobDataRunning] = (
    (__ \ 'status).format[String](pattern("running".r)) and
    (__ \ 'progress).format[Int]
  )(
    { case (_, progress) => JobDataRunning(progress) },
    { case JobDataRunning(progress) => ("running", progress) }
  )

  implicit val JobDataIdleFormat: Format[JobDataIdle.type] = new Format[JobDataIdle.type] {
    def reads(json: JsValue): JsResult[JobDataIdle.type] = json \ "status" match {
      case JsString("idle") => JsSuccess(JobDataIdle)
      case _ => JsError("was expecting { status -> idle }")
    }
    def writes(idle: JobDataIdle.type): JsValue = Json.obj("status" -> "idle")
  }

  implicit object JobDataStatusFormat extends Format[JobDataStatus] {
    def reads(json: JsValue): JsResult[JobDataStatus] =
      JobDataRunningFormat.reads(json) orElse JobDataIdleFormat.reads(json)
    def writes(jobDataStatus: JobDataStatus) = jobDataStatus match {
      case s @ JobDataRunning(_) => JobDataRunningFormat.writes(s)
      case JobDataIdle => JobDataIdleFormat.writes(JobDataIdle)
    }
  }

  implicit val JobDataFormat: Format[JobData] = (
    (__ \ '_id).format[JobId] and
    (__ \ 'name).format[String] and
    (__ \ 'entrypoint).format[URL] and
    (__ \ 'status).format[JobDataStatus] and
    (__ \ 'completedOn).formatNullable[DateTime] and
    (__ \ 'warnings).format[Int] and
    (__ \ 'errors).format[Int] and
    (__ \ 'resources).format[Int] and
    (__ \ 'maxResources).format[Int] and
    (__ \ 'health).format[Int]
  )(JobData.apply, unlift(JobData.unapply))

  implicit val RunDataFormat: Format[RunData] = (
    (__ \ 'resources).format[Int] and
    (__ \ 'errors).format[Int] and
    (__ \ 'warnings).format[Int] and
    (__ \ 'progress).format[JobDataStatus] and
    (__ \ 'completedOn).formatNullable[DateTime]
  )(RunData.apply, unlift(RunData.unapply))

  implicit val NeverStartedFormat = constant("never-started", NeverStarted)

  implicit val ZombieFormat = constant("zombie", Zombie)

  val CancelledFormat = constant("cancelled", Cancelled)
  val CompletedFormat = constant("completed", Completed)
  implicit object DoneReasonFormat extends Format[DoneReason] {
    def reads(json: JsValue): JsResult[DoneReason] =
      CancelledFormat.reads(json) orElse CompletedFormat.reads(json)
    def writes(reason: DoneReason) = reason match {
      case Cancelled => CancelledFormat.writes(Cancelled)
      case Completed => CompletedFormat.writes(Completed)
    }
  }

  val RunningFormat: Format[Running] = Json.format[Running]

  val DoneFormat: Format[Done] = (
    (__ \ 'runId).format[RunId] and
    (__ \ 'reason).format[DoneReason] and
    (__ \ 'completedOn).format[DateTime] and
    (__ \ 'runData).format[RunData]
  )(Done.apply, unlift(Done.unapply))

  implicit object JobStatusFormat extends Format[JobStatus] {
    def reads(json: JsValue): JsResult[JobStatus] =
      RunningFormat.reads(json) orElse
        NeverStartedFormat.reads(json) orElse
        DoneFormat.reads(json) orElse
        ZombieFormat.reads(json)
    def writes(jobStatus: JobStatus) = jobStatus match {
      case s@Running(_, _) => RunningFormat.writes(s)
      case NeverStarted => NeverStartedFormat.writes(NeverStarted)
      case s@Done(_, _, _, _) => DoneFormat.writes(s)
      case Zombie => ZombieFormat.writes(Zombie)
    }
  }

  implicit val JobFormat: Format[Job] = (
    (__ \ '_id).format[JobId] and
    (__ \ 'name).format[String] and
    (__ \ 'strategy).format[Strategy] and
    (__ \ 'creator).formatNullable[UserId] and
    (__ \ 'isPublic).format[Boolean] and
    (__ \ 'status).format[JobStatus] and
    (__ \ 'latestDone).formatNullable[Done](DoneFormat) and
    (__ \ 'createdOn).format[DateTime]
    )(Job.apply _, unlift(Job.unapply _))
  
  implicit val ContextFormat: Format[Context] = (
    (__ \ 'content).format[String] and
    (__ \ 'line).formatNullable[Int] and
    (__ \ 'column).formatNullable[Int]
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

  implicit val AssertionTypeIdFormat = string[AssertionTypeId](AssertionTypeId(_), _.uniqueId)

  implicit val AssertionFormat: Format[Assertion] = (
    (__ \ 'id).format[AssertionTypeId] and
    (__ \ 'url).format[URL] and
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'contexts).format[Vector[Context]] and
    (__ \ 'lang).format[String] and
    (__ \ 'title).format[String] and
    (__ \ 'severity).format[AssertionSeverity] and
    (__ \ 'description).formatNullable[String] and
    (__ \ 'timestamp).format[DateTime]
  )(Assertion.apply _, unlift(Assertion.unapply _))


  implicit val ResourcesFormat: Format[Map[URL, Int]] =
    reifiedMapFormat[URL, Int](key = "url", value = "c")

  implicit val GroupedAssertionDataFormat: Format[GroupedAssertionData] = (
    (__ \ 'id).format[AssertionTypeId] and
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'lang).format[String] and
    (__ \ 'title).format[String] and
    (__ \ 'severity).format[AssertionSeverity] and
    (__ \ 'occurrences).format[Int] and
    (__ \ 'resources).format[Map[URL, Int]](ResourcesFormat)
  )(GroupedAssertionData.apply _, unlift(GroupedAssertionData.unapply _))

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

  implicit val DoctypeFormat: Format[Doctype] = (
    (__ \ 'name).format[String] and
    (__ \ 'publicId).format[String] and
    (__ \ 'systemId).format[String]
  )(Doctype.apply _, unlift(Doctype.unapply _))

  import Format.constraints._

  implicit val HeadersFormat: Format[Headers] = new Format[Headers] {
    val format = implicitly[Format[Map[String, List[String]]]]
    def reads(json: JsValue): JsResult[Headers] = format.reads(json).map(Headers(_))
    def writes(headers: Headers): JsValue = format.writes(headers.underlying)
  }

  val HttpResponseFormat: Format[HttpResponse] = (
    (__ \ 'url).format[URL] and
    (__ \ 'method).format[HttpMethod] and
    (__ \ 'status).format[Int] and
    (__ \ 'headers).format[Headers] and
    (__ \ 'extractedURLs).format[List[URL]] and
    (__ \ 'doctype).formatNullable[Doctype]
  )(HttpResponse.apply _, unlift(HttpResponse.unapply _))

  implicit object ResourceResponseFormat extends Format[ResourceResponse] {
    def reads(json: JsValue): JsResult[ResourceResponse] =
      ErrorResponseFormat.reads(json) orElse HttpResponseFormat.reads(json)
    def writes(rr: ResourceResponse) = rr match {
      case er@ErrorResponse(_, _, _) => ErrorResponseFormat.writes(er)
      case hr@HttpResponse(_, _, _, _, _, _) => HttpResponseFormat.writes(hr)
    }
  }

  val AssertorFailureFormat: Format[AssertorFailure] = (
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'sourceUrl).format[URL] and
    (__ \ 'why).format[String]
  )(AssertorFailure.apply _, unlift(AssertorFailure.unapply _))

  def reifiedMapFormat[A, B](key: String, value: String)(implicit aFormat: StringLikeFormat[A], bFormat: Format[B]) = new Format[Map[A, B]] {

    def reads(json: JsValue): JsResult[Map[A, B]] = {
      var map: Map[A, B] = Map.empty
      json.as[JsArray].value foreach { entry =>
        val k = (entry \ key).as[A]
        val v = (entry \ value).as[B]
        map += (k -> v)
      }
      JsSuccess(map)
    }

    def writes(m: Map[A, B]): JsValue = {
      var entries: Vector[JsObject] = Vector.empty
      m foreach { case (k, v) =>
        entries :+= Json.obj(key -> toJson(k), value -> toJson(v))
      }
      JsArray(entries)
    }

  }

  implicit val AssertionsFormat: Format[Map[URL, Vector[Assertion]]] =
    reifiedMapFormat[URL, Vector[Assertion]](key = "url", value = "assertions")

  val AssertorResultFormat: Format[AssertorResult] = (
    (__ \ 'assertor).format[AssertorId] and
    (__ \ 'sourceUrl).format[URL] and
    (__ \ 'assertions).format[Map[URL, Vector[Assertion]]](AssertionsFormat)
  )(AssertorResult.apply _, unlift(AssertorResult.unapply _))

  implicit object AssertorResponseFormat extends Format[AssertorResponse] {
    def reads(json: JsValue): JsResult[AssertorResponse] =
      AssertorResultFormat.reads(json) orElse AssertorFailureFormat.reads(json)
    def writes(ar: AssertorResponse) = ar match {
      case result@AssertorResult(_, _, _) => AssertorResultFormat.writes(result)
      case failure@AssertorFailure(_, _, _) => AssertorFailureFormat.writes(failure)
    }
  }

  val CreateRunEventFormat: Format[CreateRunEvent] = (
    (__ \ 'event).format[String](pattern("create-run".r)) and
    (__ \ 'userId).formatNullable[UserId] and
    (__ \ 'jobId).format[JobId] and
    (__ \ 'runId).format[RunId] and
    (__ \ 'actorName).format[RunningActorName] and
    (__ \ 'strategy).format[Strategy] and
    (__ \ 'timestamp).format[DateTime]
  )({
    case (_, userId, jobId, runId, actorName, strategy, timestamp) =>
      CreateRunEvent(userId, jobId, runId, actorName, strategy, timestamp)
  },
    {
      case CreateRunEvent(userId, jobId, runId, actorPath, strategy, timestamp) =>
        ("create-run", userId, jobId, runId, actorPath, strategy, timestamp)
    }
  )

  val ResourceDatasFormat: Format[Map[URL, ResourceData]] =
    reifiedMapFormat[URL, ResourceData](key = "url", value = "rd")

  val DoneRunEventFormat: Format[DoneRunEvent] = (
    (__ \ 'event).format[String](pattern("done-run".r)) and
    (__ \ 'userId).formatNullable[UserId] and
    (__ \ 'jobId).format[JobId] and
    (__ \ 'runId).format[RunId] and
    (__ \ 'doneReason).format[DoneReason] and
    (__ \ 'resources).format[Int] and
    (__ \ 'errors).format[Int] and
    (__ \ 'warnings).format[Int] and
    (__ \ 'rd).format[Map[URL, ResourceData]](ResourceDatasFormat) and
    (__ \ 'gad).format[Iterable[GroupedAssertionData]] and
    (__ \ 'timestamp).format[DateTime]
  )({ case (_, userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) => DoneRunEvent(userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) },
    { case DoneRunEvent(userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) => ("done-run", userId, jobId, runId, doneReason, resources, errors, warnings, resourceDatas, groupedAssertionDatas, timestamp) }
  )

  val AssertorResponseEventFormat: Format[AssertorResponseEvent] = (
    (__ \ 'event).format[String](pattern("assertor-response".r)) and
    (__ \ 'userId).formatNullable[UserId] and
    (__ \ 'jobId).format[JobId] and
    (__ \ 'runId).format[RunId] and
    (__ \ 'ar).format[AssertorResponse] and
    (__ \ 'timestamp).format[DateTime]
  )({ case (_, userId, jobId, runId, timestamp, ar) => AssertorResponseEvent(userId, jobId, runId, timestamp, ar) }, { case AssertorResponseEvent(userId, jobId, runId, timestamp, ar) => ("assertor-response", userId, jobId, runId, timestamp, ar) } )

  val ResourceResponseEventFormat: Format[ResourceResponseEvent] = (
    (__ \ 'event).format[String](pattern("resource-response".r)) and
    (__ \ 'userId).formatNullable[UserId] and
    (__ \ 'jobId).format[JobId] and
    (__ \ 'runId).format[RunId] and
    (__ \ 'rr).format[ResourceResponse] and
    (__ \ 'timestamp).format[DateTime]
  )({ case (_, userId, jobId, runId, timestamp, rr) => ResourceResponseEvent(userId, jobId, runId, timestamp, rr) }, { case ResourceResponseEvent(userId, jobId, runId, timestamp, ar) => ("resource-response", userId, jobId, runId, timestamp, ar) })


  implicit object RunEventFormat extends Format[RunEvent] {
    def reads(json: JsValue): JsResult[RunEvent] =
      AssertorResponseEventFormat.reads(json) orElse
        ResourceResponseEventFormat.reads(json) orElse
        CreateRunEventFormat.reads(json) orElse
        DoneRunEventFormat.reads(json)
    def writes(event: RunEvent) = event match {
      case e@AssertorResponseEvent(_, _, _, _, _) => AssertorResponseEventFormat.writes(e)
      case e@ResourceResponseEvent(_, _, _, _, _) => ResourceResponseEventFormat.writes(e)
      case e@CreateRunEvent(_, _, _, _, _, _) => CreateRunEventFormat.writes(e)
      case e@DoneRunEvent(_, _, _, _, _, _, _, _, _, _) => DoneRunEventFormat.writes(e)
    }
  }

  implicit val UserFormat: Format[User] = (
    (__ \ '_id).format[UserId] and
    (__ \ 'name).format[String] and
    (__ \ 'email).format[String] and
    (__ \ 'password).format[String] and
    (__ \ 'credits).format[Int] and
    (__ \ 'optedIn).format[Boolean] and
    (__ \ 'isSubscriber).format[Boolean] and
    (__ \ 'isRoot).format[Boolean] and
    (__ \ 'registrationDate).formatNullable[DateTime] and
    (__ \ 'expireDate).format[DateTime]
  )(User.apply _, unlift(User.unapply _))

  implicit val CouponFormat: Format[Coupon] = (
    (__ \ '_id).format[CouponId] and
    (__ \ 'code).format[String] and
    (__ \ 'campaign).format[String] and
    (__ \ 'description).formatNullable[String] and
    (__ \ 'credits).format[Int] and
    (__ \ 'expirationDate).format[DateTime] and
    (__ \ 'useDate).formatNullable[DateTime] and
    (__ \ 'usedBy).formatNullable[UserId]
  )(Coupon.apply, unlift(Coupon.unapply))

}
