package controllers

import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import org.w3.vs.Graphite
import scala.concurrent.Future
import scalaz.Scalaz._
import org.w3.vs.util.timer._
import com.codahale.metrics._
import play.api.libs.json.{Json => PlayJson, JsNull, JsValue}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.{EventSource, Comet}
import org.w3.vs.model
import org.w3.vs.model._
import org.w3.vs.store.Formats._
import org.w3.vs.view.model.AssertionView
import play.api.http.{MediaRange, MimeTypes}
import org.w3.vs.exception.AccessNotAllowed

object Assertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertions")

  def index(id: JobId, url: URL): ActionA = UserAwareAction { implicit req: RequestHeader => user =>
    for {
      job_ <- model.Job.get(id)
      resource <- ResourcesView(job_, url)
      assertions <- AssertionsView(job_, url)
      assertors <- AssertorsView(id, url, assertions)
      // XXX: /!\ get rid of the cyclic dependency between assertors and assertions
      bindedAssertions = assertions.filterOn(assertors.firstAssertor).bindFromRequest
    } yield {
      render {
        case Accepts.Html() => Ok(views.html.main(
          user = user,
          title = s"Report for ${Helper.shorten(url, 50)} - W3C Validator Suite",
          crumbs = Seq(
            job_.name -> routes.Job.get(id).url,
            Helper.shorten(url, 50) -> ""),
          collections = Seq(
            resource.withAssertions(bindedAssertions),
            assertors.withCollection(bindedAssertions),
            bindedAssertions
          )))
        case Accepts.Json() => Ok(assertions.bindFromRequest.toJson)
      }
    }
  }

  def redirect(id: JobId, url: URL): ActionA = Action { implicit req =>
    Redirect(routes.Assertions.index(id, url))
  }

  def socket(jobId: JobId, url: URL, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId, url)
      case SocketType.events => eventsSocket(jobId, url)
      //case SocketType.comet => cometSocket(jobId, url)
    }
  }

  def webSocket(jobId: JobId, url: URL): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUserOption().map(user => enumerator(jobId, url, user)))
    (iteratee, enum)
  }

  /*def cometSocket(jobId: JobId, url: URL): ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(jobId, url, user) &> Comet(callback = "parent.VS.resourceupdate"))
  }}*/

  def eventsSocket(jobId: JobId, url: URL): ActionA = AsyncAction { implicit req =>
    UserAware { case user =>
      render {
        case AcceptsStream() => Ok.stream(enumerator(jobId, url, user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
      }
    }
  }

  private def enumerator(jobId: JobId, url: URL, user: Option[User]): Enumerator[JsValue /*JsArray*/] = {
    import PlayJson.toJson
    Enumerator.flatten(model.Job.getFor(jobId, user).map(
      job => job.assertions(org.w3.vs.web.URL(url))
    )) &> Enumeratee.map { iterator =>
      toJson(iterator.map(AssertionView(jobId, _).toJson))
    }
  }

}
