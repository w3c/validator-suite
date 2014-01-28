package controllers

import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.Global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import scalaz.Scalaz._
import org.w3.vs.util.timer._
import com.codahale.metrics._
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import play.api.libs.json.{Json => PlayJson, JsObject, JsNull, JsValue}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.{EventSource, Comet}
import org.w3.vs.model
import org.w3.vs.model._
import org.w3.vs.store.Formats._
import org.w3.vs.view.model.GroupedAssertionView
import play.api.http.MimeTypes
import org.w3.vs.exception._

object GroupedAssertions extends VSController  {

  val logger = play.Logger.of("controllers.GroupedAssertions")

  def index(id: JobId) = AuthenticatedAction("back.report.messages") { implicit req: RequestHeader => user =>
    for {
      job_ <- model.Job.getFor(id, Some(user))
      job <- JobsView(job_)
      groupedAssertions <- GroupedAssertionsView(job_)
      assertors <- AssertorsView(id, groupedAssertions).map(_.withCollection(groupedAssertions))
      bindedGroupedAssertions = groupedAssertions.filterOn(assertors.firstAssertor).bindFromRequest.groupBy("message")
    } yield {
      render {
        case Accepts.Html() => // XXX: /!\ get rid of the cyclic dependency between assertors and assertions
          Ok(views.html.main(
            user = user,
            title = s"""Report for job "${job_.name}" - By messages - W3C Validator Suite""",
            crumbs = Seq(job_.name -> ""),
            collections = Seq(
              job.withCollection(bindedGroupedAssertions),
              assertors.withCollection(bindedGroupedAssertions),
              bindedGroupedAssertions
            )))
        case Accepts.Json() => Ok(groupedAssertions.bindFromRequest.toJson)
      }
    }
  }

  def redirect(id: JobId): ActionA = Action { implicit req =>
    Redirect(routes.GroupedAssertions.index(id))
  }

  def socket(jobId: JobId, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId)
      case SocketType.events => eventsSocket(jobId)
    }
  }

  def webSocket(jobId: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, user)))
    (iteratee, enum)
  }

  def eventsSocket(jobId: JobId): ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case AcceptsStream() => Status(200).chunked(enumerator(jobId, user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
    }
  }

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue /*JsArray*/] = {
    import PlayJson.toJson
    Enumerator.flatten(model.Job.getFor(jobId, Some(user)).map(
      job => job.groupedAssertionDatas()
    )) &> Enumeratee.map { iterator =>
      toJson(iterator.map(GroupedAssertionView(jobId, _)))
    }
  }

}
