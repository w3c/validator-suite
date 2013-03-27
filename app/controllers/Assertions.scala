package controllers

import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import scalaz.Scalaz._
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import play.api.libs.json.{Json => PlayJson, JsObject, JsNull, JsValue}
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.{EventSource, Comet}
import org.w3.vs.model.{ Job => ModelJob, _ }
import org.w3.vs.store.Formats._
import org.w3.vs.view.model.AssertionView

object Assertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertions")

  /*def index(id: JobId, url: ): ActionA = {
    url match {
      case Some(url) => AuthAsyncAction { index_(id, url) }
      case None => AuthAsyncAction { index_(id) }
    }
  }

  def index_(id: JobId) = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      //assertions_ <- job_.getAssertions()
      job <- JobsView(job_)
      assertions <- AssertionsView(job_)
      assertors <- AssertorsView(assertions)
    } yield {
      case Html(_) => {
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withCollection(assertions), //.groupBy("message")),
            assertors.withCollection(assertions),
            assertions.filterOn(assertors.firstAssertor).bindFromRequest
          )))
      }
      case Json => {
        //val assertions = AssertionsView.grouped(assertions_, id).bindFromRequest
        Ok(assertions.bindFromRequest.toJson)
      }
    }
    f.timer(indexName).timer(indexTimer)
  } */

  def index(id: JobId, url: URL): ActionA = AuthAsyncAction { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      resource <- ResourcesView(job_, url)
      assertions <- AssertionsView(job_, url)
      assertors <- AssertorsView(id, url, assertions)
      // XXX: /!\ get rid of the cyclic dependency between assertors and assertions
      bindedAssertions = assertions.filterOn(assertors.firstAssertor).bindFromRequest
    } yield {
      case Html(_) => {
        Ok(views.html.main(
          user = user,
          title = s"Report for ${Helper.shorten(url, 50)} - Validator Suite",
          style = "",
          script = "test",
          crumbs = Seq(
            job_.name -> routes.Job.get(id),
            Helper.shorten(url, 50) -> ""),
          collections = Seq(
            resource.withAssertions(bindedAssertions),
            assertors.withCollection(bindedAssertions),
            bindedAssertions
        )))
      }
      case Json => {
        Ok(assertions.bindFromRequest.toJson)
      }
    }
    f.timer(indexUrlName).timer(indexUrlTimer)
  }

  def socket(jobId: JobId, url: URL, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId, url)
      case SocketType.events => eventsSocket(jobId, url)
      case SocketType.comet => cometSocket(jobId, url)
    }
  }

  def webSocket(jobId: JobId, url: URL): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, url, user)))
    (iteratee, enum)
  }

  def cometSocket(jobId: JobId, url: URL): ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(jobId, url, user) &> Comet(callback = "parent.VS.resourceupdate"))
  }}

  def eventsSocket(jobId: JobId, url: URL): ActionA = AuthAction { implicit req => user => {
    case Stream => Ok.stream(enumerator(jobId, url, user) &> EventSource())
  }}

  private def enumerator(jobId: JobId, url: URL, user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    Enumerator.flatten(user.getJob(jobId).map(
      job => job.assertions(org.w3.util.URL(url))
    )) &> Enumeratee.map(AssertionView(jobId, _).toJson)
  }

  val indexName = (new controllers.javascript.ReverseAssertions).index.name
  val indexTimer = Metrics.newTimer(Assertions.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertions.getClass, indexUrlName, MILLISECONDS, SECONDS)

}
