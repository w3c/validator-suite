package controllers

import org.w3.vs.model.{ Job => ModelJob, _ }
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import org.w3.vs.util.implicits.equaljURL
import org.w3.vs.Graphite
import org.w3.vs.util.timer._
import com.codahale.metrics._
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
import org.w3.vs.view.model.ResourceView

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
      job <- JobsView(job_)
      resources <- ResourcesView(job_)
      bindedResources = resources.bindFromRequest
    } yield {
      case Json => Ok(bindedResources.toJson)
      case Html(_) =>
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withCollection(bindedResources),
            bindedResources
          ))
        )
    }
    f.timer(indexName).timer(indexTimer)
  }

  def index_(id: JobId, url: URL): Request[AnyContent] => User => PartialFunction[Format, Result] = { implicit req: RequestHeader => user: User =>
    timer(indexUrlName, indexUrlTimer) {
      case Html(_) => Redirect(routes.Assertions.index(id, url))
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

  private def enumerator(jobId: JobId, urlOpt: Option[URL], user: User): Enumerator[JsValue] = urlOpt match {
    case Some(url) => enumerator(jobId, url, user)
    case None => enumerator(jobId, user)
  }

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    val enumerator = Enumerator.flatten(user.getJob(jobId).map { job =>
      job.resourceDatas()
    })
    enumerator &> Enumeratee.map { iterator =>
      toJson(iterator.map(ResourceView(jobId, _).toJson))
    }
  }

  private def enumerator(jobId: JobId, url: URL, user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    val enumerator = Enumerator.flatten(user.getJob(jobId).map { job =>
      job.resourceDatas(org.w3.vs.web.URL(url))
    })
    enumerator &> Enumeratee.map { rd =>
      PlayJson.arr(ResourceView(jobId, rd).toJson)
    }
  }

  val indexName = (new controllers.javascript.ReverseResources).index.name
  val indexTimer = Graphite.metrics.timer(MetricRegistry.name(Resources.getClass, indexName))
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Graphite.metrics.timer(MetricRegistry.name(Resources.getClass, indexUrlName))

}
