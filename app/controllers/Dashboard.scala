package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data.format.Formatter
import play.api.mvc.Request
import play.api.data.FormError
import play.api.mvc.AsyncResult
import play.api.data.Forms._
import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.mvc.PathBindable
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit._
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.vs.controllers._
import org.w3.vs.run.message._
import org.w3.vs.model.EntryPointStrategy
import akka.util.duration._
import akka.dispatch.Await
import akka.dispatch.Future
import play.api.Play.current
import play.libs.Akka._
import com.google.common.eventbus.Subscribe

object Dashboard extends Controller {
  
  def store = org.w3.vs.Prod.configuration.store
  
  val logger = play.Logger.of("Controller.Dashboard")
  
  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration

  def jobForm(user: User) = Form(
    mapping (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter)
    )((name, url, distance, linkCheck) => {
      Job(
        name = name,
        organization = user.organization,
        creator = user.id,
        strategy = new EntryPointStrategy(
          name="irrelevantForV1",
          entrypoint=url,
          distance=distance,
          linkCheck=linkCheck,
          filter=Filter(include=Everything, exclude=Nothing)))
    })
    ((job: Job) => Some(job.name, job.strategy.seedURLs.head, job.strategy.distance, job.strategy.linkCheck))
  )

  // * Indexes
  def index = IfAuth { _ => implicit user => Ok(views.html.index()) }
  
  def dashboard = IfAuth {_ => implicit user =>
    AsyncResult {
      val jobs = store.listJobs(user.organization).fold(t => throw t, jobs => jobs)
      val jobDatas = jobs map ( _.getData )
      val foo = Future.sequence(jobDatas).asPromise orTimeout("timeout", 1, SECONDS)
      foo map {either => 
        either fold(
          data => Ok(views.html.dashboard(jobs zip data)), 
          b => Results.InternalServerError(b) // TODO
        )
      }
    }
  }
  
  // * Jobs
  def newJob() = IfAuth {_ => implicit user => Ok(views.html.jobForm(jobForm(user)))}
  
  def createJob() = IfAuth {implicit req => implicit user => 
    jobForm(user).bindFromRequest.fold (
      formWithErrors => BadRequest(views.html.jobForm(formWithErrors)),
      job => {
        store.putJob(job.copy(creator = user.id, organization = user.organization)) // ?
        Redirect(routes.Dashboard.dashboard)
      }
    )
  }
  
  def jobDispatcher(id: Job#Id) = Action { request =>
    (for {
      body <- request.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "update" => updateJob(id)(request)
      case "delete" => deleteJob(id)(request)
      case "run" => runJob(id)(request)
      case "runnow" => runJob(id)(request)
      case "stop" => stopJob(id)(request)
    }).getOrElse(BadRequest("BadRequest: JobDispatcher")) // TODO error with flash
    // Can i do that in one expression?
  }
  
  def updateJob(id: Job#Id) = (IfAuth, IfJob(id)) {implicit request => implicit user => job =>
    if (user.owns(id)) {
      jobForm(user).bindFromRequest.fold (
        formWithErrors => BadRequest(views.html.jobForm(formWithErrors, Some(job))),
        newJob => {
          store.putJob(job.copy(strategy = newJob.strategy, name = newJob.name))
          Redirect(routes.Dashboard.dashboard)
          //Redirect(routes.Dashboard.dashboard.toString, 301)
        }
      )
    } else {
      Redirect(routes.Dashboard.dashboard)
    }
  }
  
  def deleteJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      store.removeJob(id)
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    } else {
      if (isAjax) InternalServerError else Redirect(routes.Dashboard.dashboard)// TODO error
    }
  }
  
  def runJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      job.getRun().start()
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    } else
      if (isAjax) InternalServerError else Redirect(routes.Dashboard.dashboard)// TODO error
  }
  
  def stopJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      // TODO
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    } else {
      if (isAjax) InternalServerError else Redirect(routes.Dashboard.dashboard)// TODO error
    }
  }
  
  def editJob(id: Job#Id) = (IfAuth, IfJob(id)) {req => implicit user => job =>
    if (user.owns(id))
      Ok(views.html.jobForm(jobForm(user).fill(job), Some(job)))
    else
      Ok(views.html.jobForm(jobForm(user).fill(job), Some(job))) // TODO error
  }
  
  // * Sockets
  def dashboardSocket() = IfAuthSocket {req => user =>
    val jobs = store.listJobs(user.organization).fold(t => throw t, jobs => jobs)
    var subs = jobs.map(_.getRun().subscribeToUpdates)
    val in = Iteratee.ignore[JsValue]
    var out = subs.reduce((e1, e2) => e1 >- e2) &> Enumeratee.collect{case e: UpdateData => e.toJS}
    (in, out)
  }
  // jobSocket
  // uriSocket
  
}