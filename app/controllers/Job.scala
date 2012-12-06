package controllers

import org.w3.vs.controllers._
import org.w3.vs.exception.InvalidFormException
import org.w3.vs.model.{Job => JobModel, User, JobId}
import org.w3.vs.view.form.JobForm
import play.Logger.ALogger
import play.api.i18n.Messages
import play.api.mvc.{WebSocket, Result, Handler, Action}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }
import java.net.URL
import play.api.libs.json.JsValue
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.{EventSource, Comet}
import org.w3.vs.actor.message.{RunCompleted, UpdateData, NewAssertorResult, RunUpdate}
import org.w3.vs.view.collection.ResourcesView
import org.w3.vs.view.model.JobView

object Job extends VSController {

  val logger: ALogger = play.Logger.of("org.w3.vs.controllers.Job")

  def reportByMessage(id: JobId): ActionA = Assertions.index(id, None)

  def reportByResource(id: JobId): ActionA = Resources.index(id, None)

  val getName = (new controllers.javascript.ReverseJob).get.name
  val getTimer = Metrics.newTimer(Job.getClass, getName, MILLISECONDS, SECONDS)

  def get(id: JobId): ActionA = AuthAction { implicit req => user =>
    timer(getName, getTimer) {
      req.getQueryString("group") match {
        case Some("message") => { case _ => Redirect(routes.Assertions.index(id, None)) }
        case _ =>               { case _ => Redirect(routes.Resources.index(id, None)) }
      }
    }
  }

  val editName = (new controllers.javascript.ReverseJob).edit.name
  val editTimer = Metrics.newTimer(Jobs.getClass, editName, MILLISECONDS, SECONDS)

  def edit(id: JobId): ActionA = AuthAsyncAction { implicit req => user =>
    val f: Future[PartialFunction[Format, Result]] = for {
      job <- user.getJob(id)
    } yield {
      case Html(_) => Ok(views.html.jobForm(JobForm.fill(job), user, Some(id)))
    }
    f.timer(editName).timer(editTimer)
  }

  def update(id: JobId): ActionA = AuthAsyncAction { implicit req => user =>
    val result: Future[PartialFunction[Format, Result]] = for {
      form <- Future(JobForm.bind match {
        case Left(form) => throw new InvalidFormException(form)
        case Right(validJobForm) => validJobForm
      })
      job_ <- user.getJob(id)
      job <- form.update(job_).save()
    } yield {
      case Html(_) => SeeOther(routes.Job.get(job.id)).flashing(("success" -> Messages("jobs.updated", job.name)))
      case _ => Ok
    }
    result recover {
      case InvalidFormException(form: JobForm, _) => {
        case Html(_) => BadRequest(views.html.jobForm(form, user, Some(id)))
        case _ => BadRequest
      }
    }
  }

  def delete(id: JobId): ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job <- user.getJob(id)
      _ <- job.delete()
    } yield {
      case Html(_) => SeeOther(routes.Jobs.index()).flashing(("success" -> Messages("jobs.deleted", job.name)))
      case _ => Ok
    }
  }

  def on(id: JobId): ActionA = simpleJobAction(id)(user => job => job.on())("jobs.on")

  def off(id: JobId): ActionA = simpleJobAction(id)(user => job => job.off())("jobs.off")

  def run(id: JobId): ActionA = simpleJobAction(id)(user => job => job.run())("jobs.run")

  def stop(id: JobId): ActionA = simpleJobAction(id)(user => job => job.cancel())("jobs.stop")

  val dispatcherName = (new controllers.javascript.ReverseJob).dispatcher.name
  val dispatcherTimer = Metrics.newTimer(Jobs.getClass, dispatcherName, MILLISECONDS, SECONDS)

  def dispatcher(implicit id: JobId): ActionA = Action { implicit req =>
    timer(dispatcherName, dispatcherTimer) {
      (for {
        body <- req.body.asFormUrlEncoded
        param <- body.get("action")
        action <- param.headOption
      } yield action.toLowerCase match {
        case "update" => update(id)(req)
        case "delete" => delete(id)(req)
        //case "on" => on(id)(req)
        //case "off" => off(id)(req)
        case "run" => run(id)(req)
        case "stop" => stop(id)(req)
        case a => BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "unknown action " + a)))))
      }).getOrElse(BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "no action parameter was specified"))))))
    }
  }

  def socket(jobId: JobId, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId)
      case SocketType.events => eventsSocket(jobId)
      case SocketType.comet => cometSocket(jobId)
    }
  }

  def webSocket(jobId: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, user)))
    (iteratee, enum)
  }

  def cometSocket(jobId: JobId): ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(jobId, user) &> Comet(callback = "parent.VS.resourceupdate"))
  }}

  def eventsSocket(jobId: JobId): ActionA = AuthAction { implicit req => user => {
    case Stream => Ok.stream(enumerator(jobId, user) &> EventSource())
  }}

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue] = {
    user.enumerator &> Enumeratee.collect[RunUpdate] {
      case UpdateData(id, data, activity) if id == jobId => JobView.toJobMessage(jobId, data, activity)
      case RunCompleted(id, completedOn) if id == jobId => JobView.toJobMessage(jobId, completedOn)
    }/*.recover[Enumerator[JsArray]]{ case _ => Enumerator.eof[JsArray] }*/ // Need help here
  }

  private def simpleJobAction(id: JobId)(action: User => JobModel => Any)(msg: String): ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job <- user.getJob(id)
      _ = action(user)(job)
    } yield {
      case Html(_) => SeeOther(routes.Job.get(job.id)).flashing(("success" -> Messages(msg, job.name)))
      case _ => Accepted
    }
  }

}
