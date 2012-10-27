package controllers

import java.net.URL
import org.w3.vs.model.JobId
import org.w3.vs.view.collection.{AssertionsView, AssertorsView, ResourcesView, JobsView}
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.view.Helper

object Resources extends VSController  {

  val logger = play.Logger.of("org.w3.vs.controllers.Resources")

  def index(id: JobId) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      job <- JobsView.single(job_)
      resources = ResourcesView(assertions_, job_.id).bindFromRequest
    } yield {
      if (isAjax) {
        Ok(resources.toJson)
      } else {
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq((job_.name, "")),
          collections = Seq(
            job.withResources(resources),
            resources
        )))
      }
    }
  }

  def get(id: JobId, url: URL) : ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job_ <- user.getJob(id)
      assertions_ <- job_.getAssertions()
      job <- JobsView.single(job_)
      resources = ResourcesView(assertions_, job_.id).bindFromRequest
    } yield {
      if (isAjax) {
        Ok(resources.toJson)
      } else {
        Ok(views.html.main(
          user = user,
          title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
          style = "",
          script = "test",
          crumbs = Seq((job_.name, "")),
          collections = Seq(
            job.withResources(resources),
            resources
        )))
      }
    }
  }

}
