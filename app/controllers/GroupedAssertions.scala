package controllers

import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.Graphite
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
import org.w3.vs.model.{ Job => ModelJob, _ }
import org.w3.vs.store.Formats._
import org.w3.vs.view.model.GroupedAssertionView
import play.api.http.MimeTypes

object GroupedAssertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.GroupedAssertions")

  def index(id: JobId) = AsyncAction { implicit req: RequestHeader =>
    Authenticated {case user: User =>
      for {
        job_ <- user.getJob(id)
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
      case AcceptsStream() => Ok.stream(enumerator(jobId, user) &> EventSource())
    }
  }

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue /*JsArray*/] = {
    import PlayJson.toJson
    Enumerator.flatten(user.getJob(jobId).map(
      job => job.groupedAssertionDatas()
    )) &> Enumeratee.map { iterator =>
      toJson(iterator.map(GroupedAssertionView(jobId, _).toJson))
    }
  }

  val indexName = (new controllers.javascript.ReverseAssertions).index.name
  val indexTimer = Graphite.metrics.timer(MetricRegistry.name(Assertions.getClass, indexName))
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Graphite.metrics.timer(MetricRegistry.name(Assertions.getClass, indexUrlName))

}
