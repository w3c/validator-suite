package controllers

import org.w3.vs.controllers._
import org.w3.vs.model
import org.w3.vs.model._
import play.api.i18n.Messages
import play.api.mvc.Action
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.json.{ Json => PlayJson, JsValue }
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.{EventSource, Comet}
import org.w3.vs.view.model.{JobView}
import org.w3.vs.store.Formats._
import play.api.http.MimeTypes
import org.w3.vs.exception.{AccessNotAllowed, PaymentRequired}

object Job extends VSController {

  val logger = play.Logger.of("controllers.Job")

//  def reportByMessage(id: JobId): ActionA = GroupedAssertions.index(id)
//  def reportByResource(id: JobId): ActionA = Resources.index(id, None)

  def get(id: JobId): ActionA = Action { implicit req =>
    req.getQueryString("group") match {
      case Some("message") => Redirect(routes.GroupedAssertions.index(id))
      case _ => Redirect(routes.Resources.index(id, None))
    }
  }

  def redirect(id: JobId): ActionA = Action { implicit req => MovedPermanently(routes.Job.get(id).url) }

  def run(id: JobId): ActionA = AuthenticatedAction { implicit req => user =>
    (for {
      job <- model.Job.getFor(id, Some(user))
      _ <-
        if (user.isRoot || (user.credits >= job.maxPages)) {
          job.run()
        } else {
          Future.failed(PaymentRequired(job))
        }
    } yield {
      render {
        case Accepts.Html() => SeeOther(routes.Job.get(job.id).url)
        case Accepts.Json() => Accepted
      }
    }) recover {
      case PaymentRequired(job) => {
        render {
          case Accepts.Html() => {
            Redirect(routes.Jobs.index()).flashing(("error" -> Messages("error.notEnoughCredits", routes.Application.pricing().url)))
          }
          case Accepts.Json() => Status(402) //(controllers.Purchase.getStoreUrl(OneTimePlan.fromJob(job), job.id))
        }
      }
    }
  }

  def stop(id: JobId): ActionA = AuthenticatedAction { implicit req => user =>
    for {
      job <- model.Job.getFor(id, Some(user))
      _ <- job.cancel()
    } yield {
      render {
        case Accepts.Html() => SeeOther(routes.Job.get(job.id).url).flashing(("success" -> Messages("jobs.stop", job.name)))
        case Accepts.Json() => Accepted
      }
    }
  }

  def delete(id: JobId): ActionA = AuthenticatedAction { implicit req => user =>
    for {
      job <- model.Job.getFor(id, Some(user))
      _ <- job.delete()
    } yield {
      render {
        case Accepts.Html() => SeeOther(routes.Jobs.index.url).flashing(("success" -> Messages("jobs.deleted", job.name)))
        case Accepts.Json() => Ok
      }
    }
  }

  import play.api.mvc._

  def dispatcher(implicit id: JobId): ActionA = Action.async { implicit req =>
    val action: String = (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase).getOrElse("notSpecified")
    action match {
      case "delete" => delete(id)(req)
      case "run" => run(id)(req)
      case "stop" => stop(id)(req)
      case a => Future.successful(BadRequest(views.html.error._400(List(("error", Messages("debug.unexpected", "unknown action " + a))))))
    }
  }

  def socket(jobId: JobId, typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket(jobId)
      case SocketType.events => eventsSocket(jobId)
    }
  }

  def webSocket(jobId: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(jobId, user)))
    (iteratee, enum)
  }

  def eventsSocket(jobId: JobId): ActionA = AuthenticatedAction{ implicit req => user =>
    render {
      case AcceptsStream() => Status(200).chunked(enumerator(jobId, user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
    }
  }

  private def enumerator(jobId: JobId, user: User): Enumerator[JsValue] = {
    import PlayJson.toJson
    Enumerator.flatten(model.Job.getFor(jobId, Some(user)).map(_.jobDatas())) &> Enumeratee.map { iterator =>
      toJson(iterator.map(JobView(_).toJson))
    }
  }

}
