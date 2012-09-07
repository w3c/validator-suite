package controllers

import org.w3.util._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.view.model._
import org.w3.vs.view.form._
import org.w3.vs.actor.message._

import play.api.i18n.Messages
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.mvc._
import scalaz.Scalaz._

object Jobs extends Controller {
  
  type ActionA = Action[AnyContent]
  
  val logger = play.Logger.of("org.w3.vs.controllers.Jobs")
  // TODO: make the implicit explicit!!!
  implicit def configuration = org.w3.vs.Prod.configuration
  
  import Application._
  
  def redirect: ActionA = Action { implicit req => Redirect(routes.Jobs.index) }
  
  def index: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
        jobs <- Job.getFor(user.id) // Job.getAll
        jobViews <- JobView.fromJobs(jobs)
      } yield {
        Ok(views.html.dashboard(Page(jobViews), user, org)).withHeaders(("Cache-Control", "no-cache, no-store"))
      }) failMap toError toPromise
    }
  }
  
  def show(id: JobId, messages: List[(String, String)] = List.empty): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        org <- user.getOrganization()
        job <- user.getJob(id)
        assertions <- job.getAssertions()
        jobView <- JobView.fromJob(job)
      } yield {
        req.getQueryString("group") match {
          case Some("message") => {
            val assertorViews = AssertorView.fromAssertions(assertions)
            val assertionViews = GroupedAssertionView.fromAssertions(assertions)
            Ok(views.html.report2(jobView, assertorViews, Page(assertionViews), user, org.get, messages)).withHeaders(("Cache-Control", "no-cache, no-store"))
          }
          /*case Some("url") => {
            val resourceViews = ResourceView.fromAssertions(assertions)
            Ok(views.html.report(jobView, Page(resourceViews), user, org.get, messages)).withHeaders(("Cache-Control", "no-cache, no-store"))
          }*/
          case _ => {
            val resourceViews = ResourceView.fromAssertions(assertions)
            Ok(views.html.report(jobView, Page(resourceViews), user, org.get, messages)).withHeaders(("Cache-Control", "no-cache, no-store"))
          }
        }
      }) failMap toError toPromise
    }
  }
  
  def report(id: JobId, url: URL, messages: List[(String, String)] = List.empty): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
        job <- user.getJob(id)
        assertions <- job.getAssertions().map(_.filter(_.url === url)) // TODO Empty = exception
        jobView <- JobView.fromJob(job)
        resourceView = ResourceView.fromAssertions(assertions).head
        assertorViews = AssertorView.fromAssertions(assertions)
        assertionViews = SingleAssertionView.fromAssertions(assertions)
      } yield {
        Ok(views.html.urlReport(jobView, resourceView, assertorViews, Page(assertionViews), user, org, messages)).withHeaders(("Cache-Control", "no-cache, no-store"))
      }) failMap toError toPromise
    }
  }
    
  def delete(id: JobId): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
        _ <- job.delete()
      } yield {
        if (isAjax) Ok else SeeOther(routes.Jobs.index.toString).flashing(("success" -> Messages("jobs.deleted", job.name)))
      }) failMap toError toPromise
    }
  } 
    
  def new1: ActionA = newOrEditJob(None)
  def edit(id: JobId): ActionA = newOrEditJob(Some(id))
  def create: ActionA = createOrUpdateJob(None)
  def update(id: JobId): ActionA = createOrUpdateJob(Some(id))
  
  def on(id: JobId): ActionA = simpleJobAction(id)(user => job => job.on())("jobs.on")
  def off(id: JobId): ActionA = simpleJobAction(id)(user => job => job.off())("jobs.off")
  def run(id: JobId): ActionA = simpleJobAction(id)(user => job => job.run())("jobs.run")
  def stop(id: JobId): ActionA = simpleJobAction(id)(user => job => job.cancel())("jobs.stop")
  
  def dispatcher(implicit id: JobId): ActionA = Action { implicit req =>
    (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "delete" => delete(id)(req)
      case "update" => update(id)(req)
      case "on" => on(id)(req)
      case "off" => off(id)(req)
      case "stop" => stop(id)(req)
      case "run" => run(id)(req)
      case a => BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "unknown action " + a))))) // TODO Logging
    }).getOrElse(BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "no action parameter was specified"))))))
  }
  
  def dashboardSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit req =>
    val promiseEnumerator: Promise[Enumerator[JsValue]] = (
      for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
      } yield {
        // ready to explode...
        // better: a user can belong to several organization. this would handle the case with 0, 1 and > 1
        org.enumerator &> Enumeratee.collect{
          case a: UpdateData => JobsUpdate.json(a.data, a.jobId, a.activity)
        }
      }
    ) failMap (_ => Enumerator.eof[JsValue]) toPromise
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }
  
  def reportSocket(id: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit req =>
    val promiseEnumerator: Promise[Enumerator[JsValue]] = (
      for {
        user <- getUser
        job <- user.getJob(id)
      } yield {
        job.enumerator &> Enumeratee.collect{
          case a: UpdateData => JobsUpdate.json(a.data, a.jobId, a.activity)
          //case NewResource(resource) => ResourceUpdate.json(resource)
          //case NewAssertions(assertionsC) if (assertionsC.count(_.assertion.severity == Warning) != 0 || assertionsC.count(_.assertion.severity == Error) != 0) => AssertorUpdate.json(assertionsC)
          case NewAssertorResult(result, datetime) if (!result.isValid) => AssertorUpdate.json(result, datetime)
        }
      }
    ) failMap (_ => Enumerator.eof[JsValue]) toPromise
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }

  /*
   * Private methods
   */
  private def newOrEditJob(implicit idOpt: Option[JobId]): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
        form <- idOpt fold (
            id => user.getJob(id) map JobForm.fill _,
            FutureVal.successful(JobForm.blank)
          )
      } yield {
        Ok(views.html.jobForm(form, user, org, idOpt))
      }) failMap toError toPromise
    }
  }
  
  private def createOrUpdateJob(implicit idOpt: Option[JobId]): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
        form <- JobForm.bind failMap (form => InvalidJobFormException(form, user, org, idOpt))
        jobM <- idOpt.fold(
            id => user.getJob(id)
              .flatMap(j => form.update(j)
              .save()
              .map(job => (job, "jobs.updated"))),
            form
              .createJob(user)
              .save()
              .map(job => (job, "jobs.created"))
          )
      } yield {
        val (job, msg) = jobM
        if (isAjax) 
          Created(views.html.libs.messages(List(("success" -> Messages(msg, job.name)))))
        else
          SeeOther(routes.Jobs.show(job.id).toString).flashing(("success" -> Messages(msg, job.name)))
      }) failMap {
        case InvalidJobFormException(form, user, org, idOpt) => BadRequest(views.html.jobForm(form, user, org, idOpt))
        case t => toError(t)
      } toPromise
    }
  }
  
  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
      } yield {
        action(user)(job)
        if (isAjax) Accepted(views.html.libs.messages(List(("success" -> Messages(msg, job.name)))))
        else        SeeOther(routes.Jobs.show(job.id).toString).flashing(("success" -> Messages(msg, job.name)))
      }) failMap toError toPromise
    }
  }
  
}
