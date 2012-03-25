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
import akka.dispatch.Await
import akka.dispatch.Future
import akka.util.duration._

object Dashboard extends Controller {

  val logger = play.Logger.of("Controller.Dashboard")

  // TODO: make the implicit explicit!!!
  implicit def configuration = org.w3.vs.Prod.configuration

  def index = Action { _ => Redirect(routes.Dashboard.dashboard) }

  def dashboard = Action { implicit req =>
    AsyncResult {
      val futureResult = for {
        user <- getAuthenticatedUser() failMap failWithGrace()
        jobs <- User.getJobs(user.organization) failMap failWithGrace(Some(user))
        viewInputs <- {
          val sortedJobs = jobs.toList.sortWith{(a, b) => a.configuration.createdOn.toString() < b.configuration.createdOn.toString()}
          val iterableFuture = sortedJobs map { job => job.jobData map (data => (job.configuration, data)) }
          val futureIterable = Future.sequence(iterableFuture)
          futureIterable.lift
        }.failMap(t => failWithGrace(Some(user))(StoreException(t)))
      } yield {
        Ok(views.html.dashboard(viewInputs, user))
      }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
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
      val futureResult =
        for {
          user <- getAuthenticatedUser failMap failWithGrace()
          job <- getJobIfAllowed(user, id) failMap failWithGrace(Some(user))
          data <- job.jobData.lift failMap {t => failWithGrace(Some(user))(Unexpected(t))}
          ars <- Job.getAssertorResults(job.id) failMap failWithGrace(Some(user))
        } yield {
          Ok(views.html.job(job.configuration, data, sort(filter(ars)), user))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise()
    }
  }
  
  def sort(ar: Iterable[AssertorResult]) = {
    ar
  }
  
  def filter(ar: Iterable[AssertorResult]) = {
    ar.collect{case a: Assertions => a}
  }

  def newJob() = newOrEditJob(None)

  def editJob(implicit id: JobId) = newOrEditJob(Some(id))

  def newOrEditJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser failMap failWithGrace()
          id <- idOpt.toImmediateSuccess(ifNone = Ok(views.html.jobForm(jobForm, user )))
          jobC <- getJobConfIfAllowed(user, id) failMap failWithGrace(Some(user))
        } yield {
          Ok(views.html.jobForm(jobForm.fill(jobC), user))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def deleteJob(implicit id: JobId) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser failMap failWithGrace()
          job <- getJobIfAllowed(user, id) failMap failWithGrace(Some(user))
          _ <- Job.delete(id) failMap failWithGrace(Some(user))
        } yield {
          seeDashboard(Ok, ("info" -> "Job deleted"))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def createJob = createOrUpdateJob(None)

  def createOrUpdateJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser failMap failWithGrace()
          jobF <- isValidForm(jobForm).toImmediateValidation failMap { formWithErrors =>
            BadRequest(views.html.jobForm(formWithErrors, user))
          }
          result <- idOpt match {
            case None =>
              for {
                _ <- Job.save(jobF.assignTo(user)) failMap failWithGrace(Some(user))
              } yield {
                seeDashboard(Created, ("info" -> "Job created"))
              }
            case Some(id) =>
              for {
                jobC <- getJobConfIfAllowed(user, id) failMap failWithGrace(Some(user))
                 _ <- Job.save(jobC.copy(strategy = jobF.strategy, name = jobF.name)) failMap failWithGrace(Some(user))
              } yield {
                seeDashboard(Ok, ("info" -> "Job updated"))
              }
          }
        } yield {
          result
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def login = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser() failMap {
            case s: StoreException => failWithGrace()(s)
            case _ => Ok(views.html.login(loginForm)) // Other exceptions are just silent
          }
        } yield {
          Redirect(routes.Dashboard.dashboard) // If the user is already logged in send him to the dashboard
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def logout = Action {
    Redirect(routes.Dashboard.login).withNewSession.flashing("success" -> "You've been logged out")
  }

  def authenticate = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          userF <- isValidForm(loginForm).toImmediateValidation failMap { formWithErrors => BadRequest(views.html.login(formWithErrors)) }
          userO <- User.authenticate(userF._1, userF._2).failMap(failWithGrace())
          user <- userO.toSuccess(Unauthorized(views.html.login(loginForm)).withNewSession).toImmediateValidation
        } yield {
          Redirect(routes.Dashboard.dashboard).withSession("email" -> user.email)
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  def onJob(implicit id: JobId) = simpleJobAction(user => job => job.on())("run on")

  def offJob(implicit id: JobId) = simpleJobAction(user => job => job.off())("run off")

  def refreshJob(implicit id: JobId) = simpleJobAction(user => job => job.refresh())("run refresh")

  def stopJob(implicit id: JobId) = simpleJobAction(user => job => job.stop())("run stop")

  private def simpleJobAction(action: User => Job => Any)(msg: String)(implicit id: JobId) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUser.failMap(failWithGrace())
          job <- getJobIfAllowed(user, id).failMap(failWithGrace(Some(user)))
        } yield {
          action(user)(job)
          seeDashboard(Accepted, ("info", msg))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  private def seeDashboard(status: Status, message: (String, String))(implicit req: Request[_]): Result = {
    if (isAjax) status else SeeOther(routes.Dashboard.dashboard.toString).flashing(message)
  }

  private def failWithGrace(authenticatedUserOpt: Option[User] = None)(e: SuiteException)(implicit req: Request[_]): Result = {
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
        else Forbidden(views.html.error(List(("error" -> "Forbidden")), authenticatedUserOpt))
      }
      case StoreException(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error" -> "Store Exception")), authenticatedUserOpt))
      }
      case Unexpected(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error" -> "Unexpected Exception")), authenticatedUserOpt))
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

  private def getJobIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, Job] =
    for {
      jobConf <- getJobConfIfAllowed(user, id)
      job <- Jobs.getJobOrCreate(jobConf).liftWith { case t => StoreException(t) }
    } yield job

  private def getJobConfIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, JobConfiguration] = {
    for {
      jobConfO <- Job.get(id)
      jobConf <- jobConfO.toSuccess(UnknownJob).toImmediateValidation
      jobConfAllowed <- {
        val validation = if (jobConf.organization === user.organization) Success(jobConf) else Failure(UnauthorizedJob)
        validation.toImmediateValidation
      }
    } yield jobConfAllowed
  }

  // TODO
  // https://github.com/playframework/Play20/wiki/Scalacache

  private def getAuthenticatedUser()(implicit session: Session): FutureValidationNoTimeOut[SuiteException, User] = {
    for {
      email <- session.get("email").toSuccess(Unauthenticated).toImmediateValidation
      userO <- User getByEmail (email)
      user <- userO.toSuccess(UnknownUser).toImmediateValidation
    } yield user
  }

  // * Sockets
  def dashboardSocket() = WebSocket.using[JsValue] { implicit req =>
    val seed = new UpdateData(null)
    
    // I don't know how to flatten a Promise[(iteratee, enumerator)] so i use another for loop for now
    val promiseIteratee = (for {
      user <- getAuthenticatedUser()
      jobConfs <- Job.getAll(user.organization)
      enumerators <- Future.sequence(jobConfs map { jobConf => Jobs.getJobOrCreate(jobConf).map(_.subscribeToUpdates) }).lift
    } yield {
      Iteratee.ignore[JsValue].mapDone[Unit]{_ => 
      	enumerators map { enum =>
      	  logger.error("Closing enumerator: " + enum)
      	  enum.close // This doesn't work: PushEnumerator:close() does not call the enumerator's onComplete method, possibly a bug.
      	}
      }
    }).failMap(_ => Iteratee.ignore[JsValue]).expiresWith(Iteratee.ignore[JsValue], 1, SECONDS).toPromiseT[Iteratee[JsValue, Unit]]

    val promiseEnumerator = (for {
      user <- getAuthenticatedUser()
      jobConfs <- Job.getAll(user.organization)
      enumerators <- Future.sequence(jobConfs map { jobConf => Jobs.getJobOrCreate(jobConf).map(_.subscribeToUpdates) }).lift
    } yield {
      val out = {
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
      out
    }).failMap(_ => Enumerator.eof[JsValue]).expiresWith(Enumerator.eof[JsValue], 1, SECONDS).toPromiseT[Enumerator[JsValue]]
    
    val enumerator: Enumerator[JsValue] = Enumerator.flatten(promiseEnumerator)
    val iteratee: Iteratee[JsValue, Unit] = Iteratee.flatten(promiseIteratee)
    
    (iteratee, enumerator)
  }

}
