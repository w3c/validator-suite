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
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import scalaz._
import Scalaz._
import Validation._
import akka.dispatch.Await
import akka.util.Duration
import play.api.data.Form

object Dashboard extends Controller {

  def store = org.w3.vs.Prod.configuration.store

  val logger = play.Logger.of("Controller.Dashboard")

  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration

  // Future  -->  Validation

  def index = Action { _ => Redirect(routes.Dashboard.dashboard) }

  def dashboard = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation.failMap{ e => failWithGrace(e) }
        jobs <- User.getJobs(user.organization).failMap( t => failWithGrace(StoreException(t)))
        viewInputs <- {
          val iterableFuture = jobs map { job => job.jobData map ( data => (job.configuration, data) ) }
          val futureIterable = Future.sequence(iterableFuture)
          futureIterable.liftWith{case t => failWithGrace(t)}
        }
      } yield {
        Ok(views.html.dashboard(viewInputs))
      }).expiresWith(failWithGrace(Timeout(new Throwable)), 1, SECONDS)
    }
  }

  // * Jobs
  def jobDispatcher(implicit id: JobId) = Action { implicit req =>
    (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "delete" => deleteJob(id)(req)
      case "update" => createOrUpdateJob(Some(id))(req)
      case "on" => onJob(id)(req)
      case "off" => offJob(id)(req)
      case "stop" => stopJob(id)(req)
      case "refresh" => refreshJob(id)(req)
      case _ => BadRequest("BadRequest: unknown action")
    }).getOrElse(BadRequest("BadRequest: JobDispatcher"))
  }

  def showReport(implicit id: JobId) = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation.failMap{ e => failWithGrace(e) }
        job <- ownsJob(user, id) failMap failWithGrace
        ars <- store.listAssertorResults(job.configuration.id).toDelayedValidation failMap (t => failWithGrace(StoreException(t)))
      } yield Ok(views.html.job(Some(job.configuration), Some(ars))())).toPromise()
    }
  }

  def newJob() = newOrEditJob(None) 
  
  def editJob(implicit id: JobId) = newOrEditJob(Some(id))
  
  def newOrEditJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation.failMap{ e => failWithGrace(e) }
        id <- idOpt.toSuccess(Ok(views.html.jobForm(jobForm))).toImmediateValidation
        job <- ownsJob(user, id) failMap failWithGrace
      } yield Ok(views.html.jobForm(jobForm.fill(job.configuration)))).toPromise
    }
  }

  def deleteJob(implicit id: JobId) = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation.failMap{ e => failWithGrace(e) }
        job <-  ownsJob(user, id) failMap failWithGrace
        _ <-  store.removeJob(id).toDelayedValidation.failMap{ t => failWithGrace(StoreException(t)) }
      } yield seeDashboard(Ok, ("info" -> "Job deleted"))).toPromise
    }
  }
  
  def createJob = createOrUpdateJob(None) 

  def createOrUpdateJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation failMap failWithGrace
        jobF <- isValidForm(jobForm).toImmediateValidation failMap { formWithErrors => BadRequest(views.html.jobForm(formWithErrors)) }
        id <- idOpt.toSuccess {
          store putJob (jobF.assignTo(user)) fold (
            t => failWithGrace(StoreException(t)),
            _ => seeDashboard(Created, ("info" -> "Job created")))
        }.toImmediateValidation
        job <- ownsJob(user, id) failMap failWithGrace
        _ <- store.putJob(job.configuration.copy(strategy = jobF.strategy, name = jobF.name)).toDelayedValidation failMap { t => failWithGrace(StoreException(t)) }
      } yield seeDashboard(Ok, ("info" -> "Job updated"))).toPromise
    }
  }

  def login = Action { implicit req =>
    isAuth.fold(
      ex => ex match {
        case s: StoreException => failWithGrace(s)
        case _ => Ok(views.html.login(loginForm)) // Other exceptions are just silent
      },
      user => Redirect(routes.Dashboard.dashboard) // If the user is already logged in send him to the dashboard
      )
  }

  def logout = Action {
    Redirect(routes.Dashboard.login).withNewSession.flashing("success" -> "You've been logged out")
  }

  def authenticate = Action { implicit req =>
    (for {
      userF <- isValidForm(loginForm) failMap { formWithErrors => BadRequest(views.html.login(formWithErrors)) }
      userO <- store.authenticate(userF._1, userF._2) failMap { t => failWithGrace(StoreException(t)) }
      user <- userO toSuccess Unauthorized(views.html.login(loginForm)).withNewSession //failWithGrace(UnknownUser)
    } yield Redirect(routes.Dashboard.dashboard).withSession("email" -> user.email)).fold(f => f, s => s)
  }

  def onJob(implicit id: JobId) = simpleJobAction(user => job => job.on())("run on")

  def offJob(implicit id: JobId) = simpleJobAction(user => job => job.off())("run off")

  def refreshJob(implicit id: JobId) = simpleJobAction(user => job => job.refresh())("run refresh")

  def stopJob(implicit id: JobId) = simpleJobAction(user => job => job.stop())("run stop")

  private def simpleJobAction(action: User => Job => Any)(msg: String)(implicit id: JobId) = Action { implicit req =>
    AsyncResult {
      (for {
        user <- isAuth.toImmediateValidation failMap failWithGrace
        job <- ownsJob(user, id) failMap failWithGrace
      } yield {
        action(user)(job)
        seeDashboard(Accepted, ("info", msg))
      }).toPromise
    }
  }

  private def seeDashboard(status: Status, message: (String, String))(implicit req: Request[_]): Result = {
    if (isAjax) status else SeeOther(routes.Dashboard.dashboard.toString).flashing(message)
  }

  private def failWithGrace[E >: SuiteException](e: E)(implicit req: Request[_]): Result = {
    e match {
      case UnknownJob => {
        if (isAjax) NotFound
        else SeeOther(routes.Dashboard.dashboard.toString).flashing(("error" -> "Unknown Job"))
      }
      case _@ (UnknownUser | Unauthenticated) => {
        if (isAjax) Unauthorized
        else Unauthorized(views.html.login(loginForm)).withNewSession
      }
      case UnauthorizedJob => {
        if (isAjax) Forbidden
        else Forbidden(views.html.error(List(("error" -> "Forbidden"))))
      }
      case StoreException(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error" -> "Store Exception"))))
      }
      case Timeout(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error" -> "Timeout Exception"))))
      }
    }
  }

  private def isValidForm[E](form: Form[E])(implicit req: Request[_]) = form.bindFromRequest.toValidation

  private def isAjax(implicit req: Request[_]) = {
    req.headers get ("x-requested-with") match {
      case Some("XMLHttpRequest") => true
      case _ => false
    }
  }

  // aren't user.organization and id the same here?
  // also, this code will get all the way to the real actor, even if only the configuration is needed
  // TODO discuss with alex the real intent for this following snippet
  private def ownsJob(user: User, id: JobId): FutureValidation[SuiteException, Job] = {
    for {
      jobConfOpt <- store.getJobById(id).failMap{ StoreException(_) }.toDelayedValidation
      jobConf <- jobConfOpt.toSuccess(UnknownJob).toImmediateValidation
      jobConfValidation <- {
        val validation = if (jobConf.organization === user.organization) Success(jobConf) else Failure(UnauthorizedJob)
        validation.toImmediateValidation
      }
      job <- Jobs.getJobOrCreate(jobConfValidation).liftWith{case t => Unexpected }
    } yield {
      job
    }
  }

  // implicit views considered harmful
  // and this one supposes that there an implicit Validation in the context
  private implicit def userOpt(implicit v: Validation[SuiteException, User]): Option[User] = v.toOption

  private implicit def isAuth(implicit session: Session): Validation[SuiteException, User] = {
    for {
      email <- session get ("email") toSuccess Unauthenticated
      userO <- store getUserByEmail (email) failMap { StoreException(_) }
      user <- userO toSuccess UnknownUser
    } yield user
  }

  def closeSocket(): (Iteratee[JsValue, _], Enumerator[JsValue]) = (Iteratee.ignore[JsValue], Enumerator.eof)

  // * Sockets
  def dashboardSocket() = WebSocket.using[JsValue] { implicit req =>
    isAuth.fold(
      f => closeSocket(),
      user => {
        val in = Iteratee.ignore[JsValue]
        val promiseEnumerator: Promise[Enumerator[JsValue]] =
          (for {
            jobConfs <- store.listJobs(user.organization).toDelayedValidation
            seed = new UpdateData(null)
            enumerators <- Future.sequence(jobConfs map { jobConf => Jobs.getJobOrCreate(jobConf).map(_.subscribeToUpdates) }).lift
            out = {
              val foo = enumerators map { (enum: Enumerator[RunUpdate]) =>
                // Filter the enumerator, taking only the UpdateData messages
                enum &> Enumeratee.collect[RunUpdate] { case e: UpdateData => e } &>
                // Transform to a tuple (updateData, sameAsPrevious)
                  Enumeratee.scanLeft[UpdateData]((seed, false)) {
                    case ((prev, _), to) if to != prev => (to, false)
                    case (_, to) => (to, true)
                  }
                // Interleave the resulting enumerators
              }
              foo.reduce((e1, e2) => e1 >- e2) &>
              // And collect messages that are marked as changed
              Enumeratee.collect { case (a, false) => a.toJS }
            }
          } yield {
            out
          }).failMap(_ => Enumerator.eof[JsValue]).toPromiseT[Enumerator[JsValue]]()
        val enumerator: Enumerator[JsValue] = Enumerator.flatten(promiseEnumerator)
        (in, enumerator)
      }
    )
  }
  // jobSocket
  // uriSocket

}
