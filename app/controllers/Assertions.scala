package controllers

import java.net.URL
import org.w3.vs.model.JobId
import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc.{ Result, Action, Handler }
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }

object Assertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertions")

  def index(id: JobId, url: Option[URL]) : ActionA = url match {
    case Some(url) => index(id, url)
    case None => index(id)
  }

  val indexName = (new controllers.javascript.ReverseAssertions).index.name
  val indexTimer = Metrics.newTimer(Assertions.getClass, indexName, MILLISECONDS, SECONDS)

  def index(id: JobId): ActionA = AuthAsyncAction { implicit req => user =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      job <- JobsView.single(job_)
    } yield {
      case Html(_) => {
        val assertors = AssertorsView(assertions_)
        val assertions = AssertionsView.grouped(assertions_, id).filterOn(assertors.firstAssertor).bindFromRequest
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq(job_.name -> ""),
          collections = Seq(
            job.withAssertions(assertions.groupBy("message")),
            assertors.withAssertions(assertions),
            assertions
          )))
      }
      case Json => {
        val assertions = AssertionsView.grouped(assertions_, id).bindFromRequest
        Ok(assertions.toJson)
      }
    }
    f.timer(indexName).timer(indexTimer)
  }

  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertions.getClass, indexUrlName, MILLISECONDS, SECONDS)

  def index(id: JobId, url: URL): ActionA = AuthAsyncAction { implicit req => user =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url))
    } yield {
      case Html(_) => {
        val assertors = AssertorsView(assertions_)
        val assertions = AssertionsView(assertions_, id, url).filterOn(assertors.firstAssertor).bindFromRequest
        val resource = ResourcesView.single(url, assertions, job_.id)
        Ok(views.html.main(
          user = user,
          title = s"Report for ${Helper.shorten(url, 50)} - Validator Suite",
          style = "",
          script = "test",
          crumbs = Seq(
            job_.name -> routes.Job.get(job_.id),
            Helper.shorten(url, 50) -> ""),
          collections = Seq(
            resource.withAssertions(assertions),
            assertors.withAssertions(assertions),
            assertions
        )))
      }
      case Json => {
        val assertions = AssertionsView(assertions_, id, url).bindFromRequest
        Ok(assertions.toJson)
      }
    }
    f.timer(indexUrlName).timer(indexUrlTimer)
  }

  def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => Action{Ok} //webSocket()
      case SocketType.events => Action{Ok} //eventsSocket()
      case SocketType.comet => Action{Ok} //cometSocket()
    }
  }

}
