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
      assertors <- AssertorsView(id, groupedAssertions).map(_.withCollection(groupedAssertions))
    } yield {
      case Html(_) => {
        // XXX: /!\ get rid of the cyclic dependency between assertors and assertions
        val bindedGroupedAssertions = groupedAssertions.filterOn(assertors.firstAssertor).bindFromRequest
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withCollection(groupedAssertions),
            assertors.withCollection(bindedGroupedAssertions),
            bindedGroupedAssertions
          )))
      }
      case Json => {
        //val assertions = AssertionsView.grouped(assertions_, id).bindFromRequest
        Ok(groupedAssertions.bindFromRequest.toJson)
      }
    }
    f.timer(indexName).timer(indexTimer)
  }

  def socket(jobId: JobId, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId)
      case SocketType.events => eventsSocket(jobId)
      case SocketType.comet => cometSocket(jobId)
    }
  }

  def webSocket(jobId: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, user)))
    (iteratee, enum)
  }

  def cometSocket(jobId: JobId): ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(jobId, user) &> Comet(callback = "parent.VS.resourceupdate"))
  }}

  def eventsSocket(jobId: JobId): ActionA = AuthAction { implicit req => user => {
    case Stream => Ok.stream(enumerator(jobId, user) &> EventSource())
  }}

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    Enumerator.flatten(user.getJob(jobId).map(
      job => job.groupedAssertionDatas() &> Enumeratee.map { assertion => toJson(assertion) }
    )) &> Enumeratee.map { json =>
      val assertor = (json \ "assertor").as[String]
      val title = (json \ "title").as[String]
      val id = (assertor + title).hashCode
      json.asInstanceOf[JsObject] + ("id", PlayJson.toJson(id))
    }
  }

  val indexName = (new controllers.javascript.ReverseAssertions).index.name
  val indexTimer = Metrics.newTimer(Assertions.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertions.getClass, indexUrlName, MILLISECONDS, SECONDS)

}
