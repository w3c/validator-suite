package controllers

import java.net.URL
import org.w3.vs.model.{ Job => ModelJob, _ }
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import org.w3.util.equaljURL
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator}
import play.api.libs.json.{Json => PlayJson, _}
import play.api.libs.{EventSource, Comet}
import scalaz.Scalaz._
import org.w3.vs.store.Formats._
import org.w3.vs.view.Helper
import org.joda.time.DateTime
import play.api.libs.json.JsUndefined
import play.api.libs.json.JsObject

object Resources extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Resources")

  def index(id: JobId, url: Option[URL]): ActionA = {
    url match {
      case Some(url) => AuthAction { index_(id, url) }
      case None => AuthAsyncAction { index_(id) }
    }
  }

  def index_(id: JobId): Request[AnyContent] => User => Future[PartialFunction[Format, Result]] = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      //assertions_ <- job_.getAssertions()
      job <- JobsView(job_)
      resources <- ResourcesView(job_)
    } yield {
      case Json => Ok(resources.toJson)
      case Html(_) => Ok(views.html.main(
        user = user,
        title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
        script = "test",
        crumbs = Seq(job_.name -> ""),
        collections = Seq(
          job.withResources(resources),
          resources.bindFromRequest
        )))
    }
    f.timer(indexName).timer(indexTimer)
  }

  def index_(id: JobId, url: URL): Request[AnyContent] => User => PartialFunction[Format, Result] = { implicit req: RequestHeader => user: User =>
    timer(indexUrlName, indexUrlTimer) {
      case Html(_) => Redirect(routes.Assertions.index(id, Some(url)))
    }
  }

  def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId, url)
      case SocketType.events => eventsSocket(jobId, url)
      case SocketType.comet => cometSocket(jobId, url)
    }
  }

  def webSocket(jobId: JobId, url: Option[URL]): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, url, user)))
    (iteratee, enum)
  }

  def cometSocket(jobId: JobId, url: Option[URL]): ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(jobId, url, user) &> Comet(callback = "parent.VS.resourceupdate"))
  }}

  def eventsSocket(jobId: JobId, url: Option[URL]): ActionA = AuthAction { implicit req => user => {
    case Stream => Ok.stream(enumerator(jobId, url, user) &> EventSource())
  }}

  private def enumerator(jobId: JobId, url: Option[URL], user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    Enumerator.flatten(user.getJob(jobId).map(job =>
      url match {
        case Some(url) => job.resourceDatas(org.w3.util.URL(url))
        case None => job.resourceDatas()
      }
    )) &> Enumeratee.map {resource =>
      val json = toJson(resource)
      val id = toJson((json \ "url").as[String].hashCode())
      // TODO: This must be implemented client side. temporary
      val lastValidated = if (!(json \ "lastValidated").isInstanceOf[JsUndefined]) {
        val timestamp = new DateTime((json \ "lastValidated").as[Long])
        PlayJson.obj(
          "timestamp" -> toJson(timestamp.toString()),
          "legend1" -> toJson(Helper.formatTime(timestamp)),
          "legend2" -> toJson("") /* the legend is hidden for now. Doesn't make sense to compute it here anyway */
        )
      } else {
        PlayJson.obj("legend1" -> toJson("Never"))
      }
      json.asInstanceOf[JsObject] +
        ("id", id) -
        "lastValidated" +
        ("lastValidated", lastValidated)
    }
  }

  val indexName = (new controllers.javascript.ReverseResources).index.name
  val indexTimer = Metrics.newTimer(Resources.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Resources.getClass, indexUrlName, MILLISECONDS, SECONDS)

}
