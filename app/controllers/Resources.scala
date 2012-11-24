package controllers

import java.net.URL
import org.w3.vs.model.{User, JobId}
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.view.Helper
import org.w3.vs.controllers._
import play.api.mvc.{WebSocket, Result, Action, Handler}
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator}
import play.api.libs.json.{JsArray, JsNull, JsValue}
import org.w3.vs.actor.message.{RunUpdate, NewAssertorResult, RunCompleted, UpdateData}
import play.api.libs.{EventSource, Comet}
import scala.Some
import org.w3.vs.actor.message.NewAssertorResult

object Resources extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Resources")

  def index(id: JobId, url: Option[URL]) : ActionA = url match {
    case Some(url) => index(id, url)
    case None => index(id)
  }

  val indexName = (new controllers.javascript.ReverseResources).index.name
  val indexTimer = Metrics.newTimer(Resources.getClass, indexName, MILLISECONDS, SECONDS)

  def index(id: JobId) : ActionA = AuthAsyncAction { implicit req => user =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      job <- JobsView.single(job_)
      resources = ResourcesView(assertions_, job_.id).bindFromRequest
    } yield {
      case Json => Ok(resources.toJson)
      case Html(_) => Ok(views.html.main(
        user = user,
        title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
        script = "test",
        crumbs = Seq(job_.name -> ""),
        collections = Seq(
          job.withResources(resources),
          resources
        )))
    }
    f.timer(indexName).timer(indexTimer)
  }

  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Resources.getClass, indexUrlName, MILLISECONDS, SECONDS)

  def index(id: JobId, url: URL): ActionA = AuthAction { implicit req => user =>
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
    Enumerator.flatten(
      (for {
        org <- user.getOrganization() map (_.get)
      } yield {
        org.enumerator &> Enumeratee.collect[RunUpdate] {
          url match {
            case None => {
              case NewAssertorResult(result, run, now) => {
                // URLs part of this assertorResult
                val urls = result.assertions.groupBy(_.url).map(_._1).toList
                // Get all the assertions we received for this urls
                val allAssertions = run.assertions.filter(a => urls.contains(a.url))
                ResourcesView(allAssertions, jobId).toJson
              }
            }
            case Some(url) => {
              case NewAssertorResult(result, run, now) if result.assertions.map(_.url).toList.contains(url) => {
                val assertionViews = AssertionsView(run.assertions.filter(_.url == url), jobId, url)
                ResourcesView.single(url, assertionViews, jobId).toJson
              }
            }
          }

        }
      }) /*.recover[Enumerator[JsArray]]{ case _ => Enumerator.eof[JsArray] }*/ // Need help here
    )
  }

}
