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
import org.w3.vs.run._
import org.w3.vs.run.message._
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

  def dashboard = Action { implicit req ⇒
    AsyncResult {
      (for {
        user ← isAuth failMap {e => Promise.pure(failwithGrace(e))}
        jobs ← store listJobs(user.organization) failMap {t => Promise.pure(failwithGrace(StoreException(t)))}
        //jobss <- Future.sequence(jobs map (_.getData))
        // That's not right, no scalaz in akka 2?
        //jobDatas: Validation[P[R], Iterable[JobData]] <- Await.result(Future.sequence(jobs map (_.getData)).onFailure[Promise[Result]]{case _ => Promise.pure(InternalServerError)}, Duration(1, SECONDS)) //.fold{t=>t} // orTimeout("timeout", 1, SECONDS)
      } yield {
        val jobDatas = jobs map (_.getData)
        val result = Future.sequence(jobDatas).asPromise orTimeout (Timeout(new Throwable), 1, SECONDS) // validation in scalaz.syntax.ValidationV -> fail[X](x): Validation[Future,X]
        result map { either ⇒
          either.fold[Result](
            data ⇒ Ok(views.html.dashboard(jobs zip data)),
            timeout ⇒ failwithGrace(timeout)
          )
        }
      }).fold(f ⇒ f, s ⇒ s)
    }
  }

  // * Jobs
  def jobDispatcher(id: Job#Id) = Action { request ⇒
    (for {
      body ← request.body.asFormUrlEncoded
      param ← body.get("action")
      action ← param.headOption
    } yield action.toLowerCase match {
      case "delete" ⇒ deleteJob(id)(request)
      case "update" ⇒ createOrUpdateJob(Some(id))(request)
      case "run"    ⇒ runJob(id)(request)
      case "runnow" ⇒ runJob(id)(request)
      case "stop"   ⇒ stopJob(id)(request)
    }).getOrElse(BadRequest("BadRequest: JobDispatcher"))
  }
  
  def showReport(implicit id: Job#Id) = Action { implicit req =>
    val view = views.html.job
    (for {
      user ← isAuth failMap failwithGrace 
      job ← ownsJob(user)(id) failMap failwithGrace
      asserts <- store.listAssertorResults(job.id) failMap (t ⇒ failwithGrace(StoreException(t)))
    } yield Ok(views.html.job(Some(job), Some(asserts))())).fold(f ⇒ f, s ⇒ s)
  }

  def editJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap failwithGrace
      id ← idOpt toSuccess Ok(views.html.jobForm(jobForm))
      job ← ownsJob(user)(id) failMap failwithGrace
    } yield Ok(views.html.jobForm(jobForm.fill(job)))).fold(f ⇒ f, s ⇒ s)
  }

  def deleteJob(implicit id: Job#Id) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap failwithGrace
      job ← ownsJob(user) failMap failwithGrace
      _ ← store.removeJob(id) failMap {t ⇒ failwithGrace(StoreException(t))}
    } yield {
      seeDashboard(Ok, ("info", "Job deleted"))
    }).fold(f ⇒ f, s ⇒ s)
  }
  
  def createOrUpdateJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap failwithGrace
      newJob ← isValidForm failMap {formWithErrors ⇒ BadRequest(views.html.jobForm(formWithErrors))}
      id ← idOpt toSuccess {
        store putJob(newJob.assignTo(user)) fold (
          t ⇒ failwithGrace(StoreException(t)),
          _ => seeDashboard(Created, ("info", "Job created"))
      )}
      job ← ownsJob(user)(id) failMap failwithGrace
      _ ← store putJob(job.copy(strategy = newJob.strategy, name = newJob.name)) failMap {t ⇒ failwithGrace(StoreException(t))}
    } yield seeDashboard(Ok, ("info", "Job updated"))).fold(f ⇒ f, s ⇒ s)
  }

  def runJob(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.run())("run started")

  def runJobNow(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.runNow())("run started")

  def stopJob(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.stop())("run stoped")

  private def simpleJobAction(action: User ⇒ Job ⇒ Any)(msg: String)(implicit id: Job#Id) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap failwithGrace
      job ← ownsJob(user) failMap failwithGrace
    } yield {
      action(user)(job)
      seeDashboard(Accepted, ("info", msg))
    }).fold(f ⇒ f, s ⇒ s)
  }
  
  private def seeDashboard(status: Status, message: (String, String))(implicit req: Request[AnyContent]): Result = {
    if (isAjax) status else Results.SeeOther(routes.Dashboard.dashboard.toString)
  }
  
  private def failwithGrace[E >: SuiteException](e: E)(implicit req: Request[AnyContent]): Result = {
    e match {
      case UnknownJob => {
        if (isAjax) NotFound
        else SeeOther(routes.Dashboard.dashboard.toString).flashing(("error", "Unknown Job"))
      }
      case c @ (UnknownUser | Unauthenticated) => {
        if (isAjax) Unauthorized
        else Unauthorized(views.html.login(Application.loginForm)).withNewSession
      }
      case UnauthorizedJob => {
        if (isAjax) Forbidden
        else Forbidden(views.html.error(List(("error", "Forbidden"))))
      }
      case StoreException(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error", "Store Exception"))))
      }
      case Timeout(t) => {
        if (isAjax) InternalServerError
        else InternalServerError(views.html.error(List(("error", "Timeout Exception"))))
      }
    }
  }

  // Move in a trait next 3
  private def isAjax(implicit req: Request[AnyContent]) = {
    req.headers get("x-requested-with") match {
      case Some("XMLHttpRequest") ⇒ true
      case _ ⇒ false
    }
  }
  
  private def ownsJob(user: User)(implicit id: Job#Id): Validation[SuiteException, Job] = {
    for {
      jobOpt ← store getJobById(id) failMap {StoreException(_)}
      job ← jobOpt toSuccess UnknownJob
      authJob: Job ← (if (job.organization == user.organization) Some(job) else None) toSuccess UnauthorizedJob //Failure(UnauthorizedJob)
    } yield authJob
  }

  private implicit def userOpt(implicit req: Request[AnyContent]): Option[User] = isAuth.toOption
  
  private implicit def isAuth(implicit req: Request[AnyContent]): Validation[SuiteException, User] = {
    for {
      email ← req.session get("email") toSuccess Unauthenticated
      userOpt ← store getUserByEmail(email) failMap {StoreException(_)}
      user ← userOpt toSuccess UnknownUser
    } yield user
  }

  private def isValidForm(implicit req: Request[AnyContent]) = {
    jobForm.bindFromRequest.toValidation
  }

  // * Sockets
  def dashboardSocket() = IfAuthSocket { req ⇒ user ⇒
      val in = Iteratee.ignore[JsValue]
      val jobs = store listJobs(user.organization) fold(t ⇒ throw t, jobs ⇒ jobs)
      // The seed for the future scan, ie the initial jobData of a run
      def seed = new UpdateData(null, null)
      // Mapping through a list of (jobId, enum)
      var out = jobs.map(job ⇒ job.getRun().subscribeToUpdates).map { enum ⇒
        // Filter the enumerator, taking only the UpdateData messages
        enum &> Enumeratee.collect[RunUpdate] { case e: UpdateData ⇒ e } &>
          // Transform to a tuple (updateData, sameAsPrevious)
          Enumeratee.scanLeft[UpdateData]((seed, false)) { (from: (UpdateData, Boolean), to: UpdateData) ⇒
            from match {
              case (prev, _) if (to != prev) ⇒ (to, false)
              case _ ⇒ (to, true)
            }
          }
        // Interleave the resulting enumerators
      }.reduce((e1, e2) ⇒ e1 >- e2) &>
        // And collect messages that are marked as changed
        Enumeratee.collect { case (a, false) ⇒ a.toJS }
      (in, out)
  }
  // jobSocket
  // uriSocket

}
