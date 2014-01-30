package controllers

import org.w3.vs.model
import org.w3.vs.model._
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import org.w3.vs.util.implicits.equaljURL
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator}
import play.api.libs.json.{Json => PlayJson, _}
import play.api.libs.{EventSource, Comet}
import scalaz.Scalaz._
import org.w3.vs.store.Formats._
import org.w3.vs.view.model.ResourceView
import play.api.http.MimeTypes

object Resources extends VSController  {

  val logger = play.Logger.of("controllers.Resources")

  def index(id: JobId, url: Option[URL]): ActionA = {
    url match {
      case Some(url) => index_(id, url)
      case None => index_(id)
    }
  }

  def redirect(id: JobId, url: Option[URL]): ActionA = Action { implicit req =>
    Redirect(routes.Resources.index(id, url))
  }

  def index_(id: JobId) = AuthenticatedAction("back.report.resources") { implicit req => user =>
    for {
      job_ <- model.Job.getFor(id, Some(user))
      job <- JobsView(job_)
      resources <- ResourcesView(job_)
      bindedResources = resources.bindFromRequest
    } yield {
      render {
        case Accepts.Html() => Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By resources - W3C Validator Suite""",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withCollection(bindedResources),
            bindedResources
          ))
        )
        case Accepts.Json() => Ok(bindedResources.toJson)
      }
    }
  }

  def index_(id: JobId, url: URL) = AuthenticatedAction { implicit req => user =>
    render {
      case Accepts.Html() => Redirect(routes.Assertions.index(id, url))
    }
  }

  def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId, url)
      case SocketType.events => eventsSocket(jobId, url)
    }
  }

  def webSocket(jobId: JobId, url: Option[URL]): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, url, user)))
    (iteratee, enum)
  }

  def eventsSocket(jobId: JobId, url: Option[URL]): ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case AcceptsStream() => Status(200).chunked(enumerator(jobId, url, user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
    }
  }

  private def enumerator(jobId: JobId, urlOpt: Option[URL], user: User, forever: Boolean = false): Enumerator[JsValue] = urlOpt match {
    case Some(url) => enumerator(jobId, url, user, forever)
    case None => enumerator(jobId, user, forever)
  }

  private def enumerator(jobId: JobId, user: User, forever: Boolean): Enumerator[JsValue] = {
    import PlayJson.toJson
    val enumerator = Enumerator.flatten(model.Job.getFor(jobId, Some(user)).map { job =>
      job.resourceDatas(forever = forever)
    })
    enumerator &> Enumeratee.map { iterator =>
      toJson(iterator.map(ResourceView(jobId, _).toJson))
    }
  }

  private def enumerator(jobId: JobId, url: URL, user: User, forever: Boolean): Enumerator[JsValue] = {
    import PlayJson.toJson
    val enumerator = Enumerator.flatten(model.Job.getFor(jobId, Some(user)).map { job =>
      job.resourceDatas(org.w3.vs.web.URL(url), forever = forever)
    })
    enumerator &> Enumeratee.map { rd =>
      PlayJson.arr(ResourceView(jobId, rd).toJson)
    }
  }

}
