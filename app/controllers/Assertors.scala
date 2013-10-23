package controllers

import org.w3.vs.model._
import org.w3.vs.view.collection.AssertorsView
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc._
import scala.concurrent.Future
import org.w3.vs.util.timer._
import org.w3.vs.util._
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import scalaz.Scalaz._

object Assertors extends VSController {

  val logger = play.Logger.of("controllers.Assertors")

  /*def index(id: JobId, url: Option[URL]): ActionA = {
    url match {
      case Some(url) => AuthAsyncAction { index_(id, url) }
      case None => AuthAsyncAction { index_(id) }
    }
  }

  def index_(id: JobId) = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      assertors = AssertorsView(id, assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
    f.timer(indexName).timer(indexTimer)
  }

  def index_(id: JobId, url: URL) = { implicit req: RequestHeader => user: User =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url.underlying === url))
      assertors = AssertorsView(assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
    f.timer(indexName).timer(indexTimer)
  }*/

  /*def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => Action{Ok} //webSocket()
      case SocketType.events => Action{Ok} //eventsSocket()
      case SocketType.comet => Action{Ok} //cometSocket()
    }
  }

  val indexName = (new controllers.javascript.ReverseAssertors).index.name
  val indexTimer = Metrics.newTimer(Assertors.getClass, indexName, MILLISECONDS, SECONDS)
  val indexUrlName = indexName + "+url"
  val indexUrlTimer = Metrics.newTimer(Assertors.getClass, indexUrlName, MILLISECONDS, SECONDS)*/

}
