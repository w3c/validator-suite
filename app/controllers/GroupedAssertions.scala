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

object GroupedAssertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.GroupedAssertions")

  def index(id: JobId) = AuthAsyncAction { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      //assertions_ <- job_.getAssertions()
      job <- JobsView(job_)
      groupedAssertions <- GroupedAssertionsView(job_)
      assertors <- AssertorsView(groupedAssertions)
    } yield {
      case Html(_) => {
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withCollection(groupedAssertions), //.groupBy("message")),
            assertors.withCollection(groupedAssertions),
            groupedAssertions.filterOn(assertors.firstAssertor).bindFromRequest
          )))
      }
      case Json => {
        //val assertions = AssertionsView.grouped(assertions_, id).bindFromRequest
        Ok(groupedAssertions.bindFromRequest.toJson)
      }
    }
    f.timer(indexName).timer(indexTimer)
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
      (url match {
        case Some(url) =>
          job.assertionDatas(org.w3.util.URL(url)) &> Enumeratee.map { assertion => toJson(assertion) }
        case None =>
          job.groupedAssertionDatas() &> Enumeratee.map { assertion => toJson(assertion) }
      }) &> Enumeratee.map { json =>
        val assertor = (json \ "assertor").as[String]
        val title = (json \ "title").as[String]
        val id = (assertor + title).hashCode
        json.asInstanceOf[JsObject] + ("id", PlayJson.toJson(id))
      }
    ))
  }

  val indexName = (new controllers.javascript.ReverseAssertions).index.name
  val indexTimer = Metrics.newTimer(Assertions.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertions.getClass, indexUrlName, MILLISECONDS, SECONDS)

}
