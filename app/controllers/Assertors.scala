package controllers

import java.net.URL
import org.w3.vs.model._
import org.w3.vs.view.collection.AssertorsView
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import play.api.mvc.{Action, Handler}

object Assertors extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertors")

  def index(id: JobId, url: Option[URL]) : ActionA = url match {
    case Some(url) => index(id, url)
    case None => index(id)
  }

  def index(id: JobId) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      assertors = AssertorsView(assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
  }

  def index(id: JobId, url: URL) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url))
      assertors = AssertorsView(assertions_)
    } yield {
      case Json => Ok(assertors.bindFromRequest.toJson)
    }
  }

  def socket(jobId: JobId, url: Option[URL], typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => Action{Ok} //webSocket()
      case SocketType.events => Action{Ok} //eventsSocket()
      case SocketType.comet => Action{Ok} //cometSocket()
    }
  }

/*  def index1(id: JobId, url: URL) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url)) // TODO Empty = exception
      assertors = AssertorsView(assertions_)
    } yield {
      Ok(assertors.bindFromRequest.toJson)
    }
  }*/

}
