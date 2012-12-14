package controllers

import java.net.URL
import org.w3.vs.model._
import org.w3.vs.view.collection.AssertorsView
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import org.w3.vs.model

object Assertors extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertors")

  def index(id: JobId, url: Option[URL]): ActionA = {
    if (id == model.Job.sample.id) {
      VSAction { req => {
        case Html(_) => Redirect(routes.Assertors.sample(url))
        case _ => sample(url)(req)
      }}
    } else {
      url match {
        case Some(url) => AuthAsyncAction { index_(id, url) }
        case None => AuthAsyncAction { index_(id) }
      }
    }
  }

  def sample(url: Option[URL]): ActionA = AsyncAction { implicit req =>
    val sampleId = model.Job.sample.id
    val sampleUser = User.sample
    url match {
      case Some(url) => index_(sampleId, url)(req)(sampleUser)
      case None => index_(sampleId)(req)(sampleUser)
    }
  }

  def index_(id: JobId) = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      assertors = AssertorsView(assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
    f.timer(indexName).timer(indexTimer)
  }

  def index_(id: JobId, url: URL) = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url))
      assertors = AssertorsView(assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
    f.timer(indexName).timer(indexTimer)
  }

  def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => Action{Ok} //webSocket()
      case SocketType.events => Action{Ok} //eventsSocket()
      case SocketType.comet => Action{Ok} //cometSocket()
    }
  }

  val indexName = (new controllers.javascript.ReverseAssertors).index.name
  val indexTimer = Metrics.newTimer(Assertors.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertors.getClass, indexUrlName, MILLISECONDS, SECONDS)

}
