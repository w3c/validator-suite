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
  def index = Action { implicit req ⇒
    isAuth.fold[Result](
      e ⇒ InternalServerError(e),
      user ⇒ Ok(views.html.index()(user)))
  }

  // Future  -->  Validation

  def dashboard = Action { implicit req ⇒
    AsyncResult {
      (for {
        user ← isAuth failMap { e ⇒ Promise.pure(InternalServerError) }
        jobs ← store.listJobs(user.organization) failMap { e ⇒ Promise.pure(InternalServerError) }
        // That's not right, no scalaz in akka 2?
        //jobDatas: Validation[P[R], Iterable[JobData]] <- Await.result(Future.sequence(jobs map (_.getData)).onFailure[Promise[Result]]{case _ => Promise.pure(InternalServerError)}, Duration(1, SECONDS)) //.fold{t=>t} // orTimeout("timeout", 1, SECONDS)
      } yield {
        val jobDatas = jobs map (_.getData)
        val foo = Future.sequence(jobDatas).asPromise orTimeout ("timeout", 1, SECONDS)
        foo map { either ⇒
          either.fold[Result](
            data ⇒ Ok(views.html.dashboard(jobs zip data)(user)),
            b ⇒ Results.InternalServerError(b) // TODO
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
      case "run" ⇒ runJob(id)(request)
      case "runnow" ⇒ runJob(id)(request)
      case "stop" ⇒ stopJob(id)(request)
    }).getOrElse(BadRequest("BadRequest: JobDispatcher"))
  }

  def editJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap { e ⇒ InternalServerError(e) }
      id ← idOpt toSuccess Ok(views.html.jobForm(jobForm(user))(user, idOpt))
      job ← ownsJob(user)(id) failMap { e ⇒ InternalServerError(e) }
    } yield Ok(views.html.jobForm(jobForm(user).fill(job))(user, idOpt))).fold(f ⇒ f, s ⇒ s)
  }

  def deleteJob(implicit id: Job#Id) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap { e ⇒ InternalServerError(e) }
      job ← ownsJob(user) failMap { e ⇒ InternalServerError(e) }
      _ ← store.removeJob(id) failMap { t ⇒ InternalServerError(t.getMessage) }
    } yield {
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }).fold(f ⇒ f, s ⇒ s)
  }

  import play.api.data.Form

  class FormW[T](form: Form[T]) {
    def toValidation: Validation[Form[T], T] =
      form.fold(f ⇒ Failure(f), s ⇒ Success(s))
  }

  implicit def toFormW[T](form: Form[T]): FormW[T] = new FormW(form)

  def createOrUpdateJob(implicit idOpt: Option[Job#Id] = None) = Action { implicit req ⇒
    val r: Validation[Result, Result] = for {
      user ← isAuth.failMap { e ⇒ InternalServerError(e) }
      newJob ← jobForm(user).bindFromRequest.toValidation.failMap[Result] { formWithErrors ⇒ BadRequest(views.html.jobForm(formWithErrors)(user, idOpt)) }
      id ← idOpt toSuccess {
        store.putJob(newJob.copy(creator = user.id, organization = user.organization))
        if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
      }
      job ← ownsJob(user)(id) failMap { e ⇒ InternalServerError(e) }
      _ ← store.putJob(job.copy(strategy = newJob.strategy, name = newJob.name)) failMap { t ⇒ InternalServerError("") }
    } yield {
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }
    r.fold(f ⇒ f, s ⇒ s)
  }

  def runJob(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.run())

  def runJobNow(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.runNow())

  def stopJob(implicit id: Job#Id) = simpleJobAction(user ⇒ job ⇒ job.stop())

  private def simpleJobAction(action: User ⇒ Job ⇒ Any)(implicit id: Job#Id) = Action { implicit req ⇒
    (for {
      user ← isAuth failMap { e ⇒ InternalServerError(e) }
      job ← ownsJob(user) failMap { e ⇒ InternalServerError(e) }
    } yield {
      action(user)(job)
      if (isAjax) Ok else Redirect(routes.Dashboard.dashboard)
    }).fold(f ⇒ f, s ⇒ s)
  }

  // Move in a trait next 3
  def isAjax(implicit req: Request[AnyContent]) = {
    req.headers.get("x-requested-with") match {
      case Some("XMLHttpRequest") ⇒ true
      case _ ⇒ false
    }
  }

  def ownsJob(user: User)(implicit id: Job#Id) = {
    for {
      jobOpt ← store.getJobById(id) failMap { t ⇒ "store exception" }
      job ← jobOpt toSuccess { "unknown jobid" }
      owns ← (if (job.organization == user.organization) Some(true) else None) toSuccess { "unauthorized" }
    } yield job
  }

  def isAuth(implicit req: Request[AnyContent]) = {
    for {
      email ← req.session.get("email") toSuccess "not authenticated"
      userOpt ← store.getUserByEmail(email) failMap { t ⇒ "store exception" }
      user ← userOpt toSuccess "unknown user"
    } yield user
  }

  // * Sockets
  def dashboardSocket() = IfAuthSocket { req ⇒
    user ⇒
      val in = Iteratee.ignore[JsValue]
      val jobs = store.listJobs(user.organization).fold(t ⇒ throw t, jobs ⇒ jobs)
      // The seed for the future scan, ie the initial jobData of a run
      def seed(id: Job#Id) = new UpdateData(JobData.Default, id)
      // Mapping through a list of (jobId, enum)
      var out = jobs.map(job ⇒ (job.id, job.getRun().subscribeToUpdates)).map { tuple ⇒
        val (jobId, enum) = tuple
        // Filter the enumerator, taking only the UpdateData messages
        enum &> Enumeratee.collect[RunUpdate] { case e: UpdateData ⇒ e } &>
          // Transform to a tuple (updateData, sameAsPrevious)
          Enumeratee.scanLeft[UpdateData]((seed(jobId), false)) { (from: (UpdateData, Boolean), to: UpdateData) ⇒
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
