package controllers

import java.net.URL
import org.w3.vs.model.{AssertionId, JobId}
import org.w3.vs.view.Helper
import org.w3.vs.view.collection._
import scala.concurrent.ExecutionContext.Implicits.global

object Assertions extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Assertions")

  def index(id: JobId) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      job <- JobsView.single(job_)
      assertions_ <- job_.getAssertions()
    } yield {
      if (isAjax) {
        val assertions = AssertionsView.grouped(assertions_, id).bindFromRequest
        Ok(assertions.toJson)
      } else {
        val assertors = AssertorsView(assertions_)
        val assertions = AssertionsView.grouped(assertions_, id).filterOn(assertors.firstAssertor).bindFromRequest
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq((job_.name, "")),
          collections = Seq(
            job.withAssertions(assertions.groupBy("message")), // TODO look into getting rid of the groupBy
            assertors.withAssertions(assertions),
            assertions
        )))
      }
    }
  }

  def index1(id: JobId, url: URL) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions().map(_.filter(_.url == url)) // TODO Empty = exception
      assertors = AssertorsView(assertions_)
    } yield {
      if (isAjax) {
        val assertions = AssertionsView(assertions_, id, url).bindFromRequest
        Ok(assertions.toJson)
      } else {
        val assertions = AssertionsView(assertions_, id, url).filterOn(assertors.firstAssertor).bindFromRequest
        val resource = ResourcesView.single(url, assertions, job_.id)
        Ok(views.html.main(
          user = user,
          title = s"Report for ${Helper.shorten(url, 50)} - Validator Suite",
          style = "",
          script = "test",
          crumbs = Seq(
            (job_.name -> routes.Resources.index(job_.id).toString),
            (Helper.shorten(url, 50) -> "")),
          collections = Seq(
            resource.withAssertions(assertions),
            assertors.withAssertions(assertions),
            assertions
        )))
      }
    }
  }

  def get(job: JobId, assertionId: AssertionId) : ActionA = TODO

  def get1(job: JobId, url: URL, assertionId: AssertionId) : ActionA = TODO

}
