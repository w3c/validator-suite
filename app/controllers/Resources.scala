package controllers

import java.net.URL
import org.w3.vs.model.JobId
import org.w3.vs.view.collection.{AssertionsView, AssertorsView, ResourcesView, JobsView}
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.view.Helper
import org.w3.vs.controllers._
import play.api.mvc.{ Result, Action, Handler }
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }

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
      case SocketType.ws => Action{Ok} //webSocket()
      case SocketType.events => Action{Ok} //eventsSocket()
      case SocketType.comet => Action{Ok} //cometSocket()
    }
  }

}
