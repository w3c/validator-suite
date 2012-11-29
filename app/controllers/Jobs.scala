package controllers

import org.w3.vs.actor.message._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.collection._
import org.w3.vs.view.form._
import org.w3.vs.view.model._
import play.Logger.ALogger
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.{EventSource, Comet}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import scala.concurrent.Future
import org.w3.util.Util._
import com.yammer.metrics.Metrics
import java.util.concurrent.TimeUnit.{ MILLISECONDS, SECONDS }

object Jobs extends VSController {
  
  val logger: ALogger = play.Logger.of("org.w3.vs.controllers.Jobs")

  val indexName = (new controllers.javascript.ReverseJobs).index.name
  val indexTimer = Metrics.newTimer(Jobs.getClass, indexName, MILLISECONDS, SECONDS)

  def index: ActionA = AuthAsyncAction { implicit req => user =>
    val f: Future[PartialFunction[Format, Result]] = for {
      jobs_ <- user.getJobs()
      jobs <- JobsView(jobs_)
    } yield {
      case Html(_) => {
        Ok(views.html.main(
          user = user,
          title = "Jobs - Validator Suite",
          script = "test",
          collections = Seq(jobs.bindFromRequest)
        ))
      }
      case Json => Ok(jobs.bindFromRequest.toJson)
      case Rdf => TODO(req) // TODO
    }
    f.timer(indexName).timer(indexTimer)
  }

  val newJobName = (new controllers.javascript.ReverseJobs).newJob.name
  val newJobTimer = Metrics.newTimer(Jobs.getClass, newJobName, MILLISECONDS, SECONDS)

  def newJob: ActionA = AuthAction { implicit req => user =>
    timer(newJobName, newJobTimer) {
      case Html(_) => Ok(views.html.jobForm(JobForm.blank, user, None))
    }
  }

  val createName = (new controllers.javascript.ReverseJobs).create.name
  val createTimer = Metrics.newTimer(Jobs.getClass, createName, MILLISECONDS, SECONDS)

  def create: ActionA = AuthAsyncAction { implicit req => user =>
    val f1: Future[PartialFunction[Format, Result]] =
      for {
        form <- Future(JobForm.bind match {
          case Left(form) => throw new InvalidFormException(form)
          case Right(validJobForm) => validJobForm
        })
        job <- form.createJob(user).save()
      } yield {
        case Html(_) => SeeOther(routes.Jobs.index()) /*.flashing(("success" -> Messages("jobs.created", job.name)))*/
        case _ => Created(routes.Job.get(job.id).toString)
      }
    val f2: Future[PartialFunction[Format, Result]] = f1 recover {
      case InvalidFormException(form: JobForm) => {
        case format @ Html(_) => BadRequest(views.html.jobForm(form, user, None))
        case _ => BadRequest
      }
    }
    f2.timer(createName).timer(createTimer)
  }

  def socket(typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket()
      case SocketType.events => eventsSocket()
      case SocketType.comet => cometSocket()
    }
  }

  def webSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(user)))
    (iteratee, enum)
  }

  def cometSocket: ActionA = AuthAction { implicit req => user => {
    case Html(_) => Ok.stream(enumerator(user) &> Comet(callback = "parent.VS.jobupdate"))
  }}

  def eventsSocket: ActionA = AuthAction { implicit req => user => {
    case Stream => Ok.stream(enumerator(user) &> EventSource()) //.as("text/event-stream")
  }}

  private def enumerator(user: User): Enumerator[JsValue] = {
    user.enumerator &> Enumeratee.collect {
      case a: UpdateData => JsArray(List(JobView.toJobMessage(a.jobId, a.data, a.activity)))
      case a: RunCompleted => JsArray(List(JobView.toJobMessage(a.jobId, a.completedOn)))
    }/*.recover{ case _ => Enumerator.eof[JsValue] }*/
  }

}
