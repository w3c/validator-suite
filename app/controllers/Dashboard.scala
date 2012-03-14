package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import akka.dispatch.Future
import java.util.concurrent.TimeUnit._
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.controllers._
import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.vs.run.message._
import scalaz._
import Scalaz._
import Validation._
import akka.dispatch.Await
import akka.util.Duration

object Dashboard extends Controller {
  
  def store = org.w3.vs.Prod.configuration.store
  
  val logger = play.Logger.of("Controller.Dashboard")
  
  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration

  // * Indexes
  def index = Action { _ => Ok(views.html.index()) }
  
  def dashboard = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth failMap {e => Promise.pure(InternalServerError)}
        jobs <- store.listJobs(user.organization) failMap {e => Promise.pure(InternalServerError)}
        // That's not right, no scalaz in akka 2?
        //jobDatas <- Await.result(Future.sequence(jobs map (_.getData)).onFailure[Promise[Result]]{case _ => Promise.pure(InternalServerError)}, Duration(1, SECONDS)) //.fold{t=>t} // orTimeout("timeout", 1, SECONDS)
      } yield {
        val jobDatas = jobs map ( _.getData )
        val foo = Future.sequence(jobDatas).asPromise orTimeout("timeout", 1, SECONDS)
        foo map {either => 
          either.fold[Result] (
            data => Ok(views.html.dashboard(jobs zip data)(user)), 
            b => Results.InternalServerError(b) // TODO
          )
        }
      }).fold(f=>f,s=>s)
    }
  }
  
  // * Jobs
  def jobDispatcher(id: Job#Id) = Action { request =>
    (for {
      body <- request.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "update" => createOrUpdateJob(Some(id))(request)
      case "delete" => deleteJob(id)(request)
      case "run"    => runJob(id)(request)
      case "runnow" => runJob(id)(request)
      case "stop"   => stopJob(id)(request)
    }).getOrElse(BadRequest("BadRequest: JobDispatcher"))
  }
  
  def createOrUpdateJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req =>
    (for {
      user <- isAuth failMap {e => InternalServerError(e)}
      id <- idOpt toSuccess {
          jobForm(user).bindFromRequest.fold[Result] (
            formWithErrors => BadRequest(views.html.jobForm(formWithErrors)(user, idOpt)),
            job => {
              store.putJob(job.copy(creator = user.id, organization = user.organization))
              if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
          })}
      job <- ownsJob(user)(id) failMap {e => InternalServerError(e)}
    } yield {
      jobForm(user).bindFromRequest.fold[Result] (
        formWithErrors => Ok(views.html.jobForm(formWithErrors)(user, idOpt)),
        newJob => {
          store.putJob(job.copy(strategy = newJob.strategy, name = newJob.name))
          if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
      })
    }).fold(f=>f,s=>s)
  }
  
  // refactorable
  def deleteJob(implicit id: Job#Id) = Action { implicit req =>
    (for {
      user <- isAuth failMap {e => InternalServerError(e)}
      job <- ownsJob(user) failMap {e => InternalServerError(e)}
    } yield {
      store.removeJob(id)
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }).fold(f=>f,s=>s) 
  }

  def runJob(implicit id: Job#Id) = Action { implicit req =>
    (for {
      user <- isAuth failMap {e => InternalServerError(e)}
      job <- ownsJob(user) failMap {e => InternalServerError(e)}
    } yield {
      job.runNow()
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }).fold(f=>f,s=>s)
  }
  
  def stopJob(implicit id: Job#Id) = Action { implicit req =>
    (for {
      user <- isAuth failMap {e => InternalServerError(e)}
      job <- ownsJob(user) failMap {e => InternalServerError(e)}
    } yield {
      job.stop()
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }).fold(f=>f,s=>s)
  }
  
  def editJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req =>
    (for {
      user <- isAuth failMap {e => InternalServerError(e)}
      id <- idOpt toSuccess Ok(views.html.jobForm(jobForm(user))(user, idOpt))
      job <- ownsJob(user)(id) failMap {e => InternalServerError(e)}
    } yield Ok(views.html.jobForm(jobForm(user).fill(job))(user, idOpt))).fold(f=>f,s=>s)
  }
  
  // Move in a trait next 3
  def isAjax(implicit req: Request[AnyContent]) = {
     req.headers.get("x-requested-with") match {
      case Some("XMLHttpRequest") => true
      case _ => false
    }
  }
  
  // def runJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
  //   if (user.owns(id)) {
  //     job.getRun().runNow()
  //     if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
  //   } else
  //     if (isAjax) InternalServerError else Redirect(routes.Dashboard.dashboard)// TODO error

  def ownsJob(user: User)(implicit id: Job#Id) = {
    for {
      jobOpt <- store.getJobById(id) failMap { t => "store exception" }
      job <- jobOpt toSuccess { "unknown jobid" }
      owns <- (if (job.organization == user.organization) Some(true) else None) toSuccess { "unauthorized" }
    } yield job
  }
  
  def isAuth(implicit req: Request[AnyContent]) = {
    for {
      email <- req.session.get("email") toSuccess "not authenticated"
      userOpt <- store.getUserByEmail(email) failMap { t => "store exception" }
      user <- userOpt toSuccess "unknown user"
    } yield user
  }
  
  // * Sockets
  def dashboardSocket() = IfAuthSocket {req => user =>
    val in = Iteratee.ignore[JsValue]
    val jobs = store.listJobs(user.organization).fold(t => throw t, jobs => jobs)
    // The seed for the future scan, ie the initial jobData of a run
    def seed(id: Job#Id) = new UpdateData(JobData.Default, id)
    // Mapping through a list of (jobId, enum)
    var out = jobs.map(job => (job.id, job.getRun().subscribeToUpdates)).map { tuple =>
      val (jobId, enum) = tuple
      // Filter the enumerator, taking only the UpdateData messages
      enum &> Enumeratee.collect[RunUpdate]{case e: UpdateData => e} &>
        // Transform to a tuple (updateData, sameAsPrevious)
        Enumeratee.scanLeft[UpdateData]((seed(jobId), false)) {(from: (UpdateData, Boolean), to: UpdateData) => 
          from match {
            case (prev, _) if (to != prev) => (to, false)
            case _ => (to, true)
          }
        }
    // Interleave the resulting enumerators
    }.reduce((e1, e2) => e1 >- e2) &>
    // And collect messages that are marked as changed
      Enumeratee.collect{case (a, false) => a.toJS} 
    (in, out)
  }
  // jobSocket
  // uriSocket
  
}
