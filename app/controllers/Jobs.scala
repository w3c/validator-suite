package controllers

import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.collection._
import org.w3.vs.view.form._
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.{Json => PlayJson, _}
import play.api.libs.{EventSource, Comet}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import scala.concurrent.Future
import org.w3.vs.util.timer._
import org.w3.vs.Graphite
import com.codahale.metrics._
import org.w3.vs.view.model.JobView
import org.w3.vs.store.Formats._
import play.api.i18n.Messages
import play.api.http.MimeTypes

object Jobs extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Jobs")

  val indexName = (new controllers.javascript.ReverseJobs).index.name
  val indexTimer = Graphite.metrics.timer(MetricRegistry.name(Jobs.getClass, indexName))

  def index: ActionA = AuthenticatedAction { implicit req => user =>
    for {
      jobs_ <- user.getJobs()
      jobs <- JobsView(jobs_)
    } yield {
      render {
        case Accepts.Html() => Ok(views.html.main(
          user = user,
          title = "Jobs - W3C Validator Suite",
          collections = Seq(jobs.bindFromRequest)
        ))
        case Accepts.Json() => Ok(jobs.bindFromRequest.toJson)
      }
    }
  }

  def redirect(): ActionA = Action {
    implicit req =>
      Redirect(routes.Jobs.index)
  }

  val newJobName = (new controllers.javascript.ReverseJobs).newJob.name
  val newJobTimer = Graphite.metrics.timer(MetricRegistry.name(Jobs.getClass, newJobName))

  def newJob: ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case Accepts.Html() => {
        if (user.isSubscriber) {
          Ok(views.html.newJob(JobForm.blank, user))
        } else {
          Redirect(routes.OneTimeJob.buy.url)
          //Ok(views.html.newJobOneTime(OneTimeJobForm.blank, user))
        }
      }
    }
  }

  def create: ActionA = AuthenticatedAction { implicit req => user =>
    (for {
      form <- Future(JobForm.bind match {
        case Left(form) => throw new InvalidFormException(form)
        case Right(validJobForm) => validJobForm
      })
      job <- form.createJob(user).save()
    } yield {
      render {
        case Accepts.Html() => SeeOther(routes.Jobs.index.url) /*.flashing(("success" -> Messages("jobs.created", job.name)))*/
        case Accepts.Json() => Created(routes.Job.get(job.id).toString)
      }
    }) recover {
      case InvalidFormException(form: JobForm, _) => {
        render {
          case Accepts.Html() => BadRequest(views.html.newJob(form, user))
          case Accepts.Json() => BadRequest
        }
      }
    }
  }

  /*val createName = (new controllers.javascript.ReverseJobs).create.name
  val createTimer = Graphite.metrics.timer(MetricRegistry.name(Jobs.getClass, createName))

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
      case InvalidFormException(form: JobForm, _) => {
        case Html(_) => BadRequest(views.html.newJob(form, user))
        case _ => BadRequest
      }
    }
    f2.timer(createName).timer(createTimer)
  }*/

  def socket(typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket()
      case SocketType.events => eventsSocket()
    }
  }

  def webSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    // TODO Authenticate
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(user)))
    (iteratee, enum)
    ???
  }

  def eventsSocket: ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case AcceptsStream() => Ok.stream(enumerator(user) &> EventSource())
    }
  }

  private def enumerator(user: User): Enumerator[JsValue] = {
    user.jobDatas() &> Enumeratee.map {
      iterator =>
        PlayJson.toJson(iterator.map(JobView(_).toJson))
    }
  }

}
