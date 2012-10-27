package controllers

import java.net.URL
import org.w3.vs.model._
import org.w3.vs.view.collection.AssertorsView
import scala.concurrent.ExecutionContext.Implicits.global

object Assertors extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertors")

  def index(id: JobId) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      assertors = AssertorsView(assertions_)
    } yield {
      Ok(assertors.bindFromRequest.toJson)
    }
  }

  def index1(id: JobId, url: URL) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url)) // TODO Empty = exception
      assertors = AssertorsView(assertions_)
    } yield {
      Ok(assertors.bindFromRequest.toJson)
    }
  }

}
