package controllers

import org.w3.vs.{Metrics, model}
import org.w3.vs.model._
import org.w3.vs.view.collection._
import org.w3.vs.view.Forms._
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}
import play.api.libs.json.{Json => PlayJson, _}
import play.api.libs.{EventSource, Comet}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global
import org.w3.vs.controllers._
import scala.concurrent.{Promise, Future}
import org.w3.vs.view.model.JobView
import org.w3.vs.store.Formats._
import play.api.i18n.Messages
import play.api.http.MimeTypes
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE
import scala.util.Try
import java.{net, util}
import play.api.i18n.Messages.Message
import org.w3.vs.web.Headers
import org.w3.vs.assertor.LocalValidators.ValidatorNu

object Jobs extends VSController {

  val logger = play.Logger.of("controllers.Jobs")

  private def lowCreditWarning(credits: Int) = {
    if (credits <= 0)
      List(("warn" -> Messages("warn.noCredits", routes.Application.pricing().url)))
    else if (credits <= 50)
      List(("warn" -> Messages("warn.lowCredits", credits, routes.Application.pricing().url)))
    else
      List.empty
  }

  def index: ActionA = AuthenticatedAction("back.jobs") { implicit req => user =>
    for {
      jobs_ <- model.Job.getFor(user.id)
      jobs <- JobsView(jobs_)
    } yield {
      render {
        case Accepts.Html() =>
          Ok(views.html.main(
            user = user,
            title = "Jobs - W3C Validator Suite",
            collections = Seq(jobs.bindFromRequest),
            messages = lowCreditWarning(user.credits)
          ))
        case Accepts.Json() => Ok(jobs.bindFromRequest.toJson)
      }
    }
  }

  def redirect(): ActionA = Action { implicit req => MovedPermanently(routes.Jobs.index().url) }

  def newJob: ActionA = AuthenticatedAction("back.newJob") { implicit req => user =>
    render {
      case Accepts.Html() => {
        Ok(views.html.newJob(
          form = JobForm(user),
          user = user,
          messages = lowCreditWarning(user.credits)
        ))
      }
    }
  }

  case class EntrypointException(url: URL, message: String, args: String*) extends Exception(message)

  def checkEntrypoint(url: URL): Future[URL] = {
    val promise: Promise[URL] = akka.dispatch.Futures.promise[URL]()
    var code: Int = 0
    val handler = new AsyncHandler[Unit]() {
      def onThrowable(p1: Throwable) {
        if (!promise.isCompleted) {
          promise.complete(Try(p1 match {
            case e: EntrypointException => throw e
            case _ => throw new EntrypointException(url, "error.exception", p1.getMessage)
          }))
        }
      }
      def onStatusReceived(p1: HttpResponseStatus): STATE = {
        code = p1.getStatusCode
        if (code == 200) {
          STATE.CONTINUE
        } else if (300 <= code && code < 400) {
          // We found a redirection, let's continue and check the location header
          STATE.CONTINUE
        } else {
          promise.complete(Try(throw new EntrypointException(url, "error.invalidCode", p1.getStatusCode.toString)))
          STATE.ABORT
        }
      }
      def onHeadersReceived(p1: HttpResponseHeaders): STATE = {
        import org.w3.vs.web.URL
        import org.w3.vs.web.URL._
        import scala.collection.JavaConversions._

        val headers = Headers(p1.getHeaders)

        if (code == 200) {

          // Check the mimetype
          if (!headers.mimetype.isDefined) {
            throw new EntrypointException(url, "error.mimetype.notFound")
          } else if (!ValidatorNu.supportedMimeTypes.contains(headers.mimetype.get)) {
            throw new EntrypointException(url, "error.mimetype.unsupported", headers.mimetype.get, ValidatorNu.supportedMimeTypes.mkString("", ", ", ""))
          }
          promise.complete(Try(url))

        } else { // It's a redirection

          // Check the new location
          val location = headers.locationURL(url)

          if (!location.isDefined) {
            throw new EntrypointException(url, "error.location.notFound")
          } else if (location.get.authority != url.authority) {
            throw new EntrypointException(location.get, "error.location.newDomain", url.toString)
          } else if (!location.get.getAuthority.startsWith(url.getAuthority)) {
            throw new EntrypointException(location.get, "error.location.upperLevel")
          }
          promise.complete(Try(location.get))

        }
        STATE.ABORT
      }
      def onCompleted() {
        if (!promise.isCompleted) {
          promise.complete(Try(throw new Exception("Promise was not completed by the end of the response")))
        }
      }
      def onBodyPartReceived(p1: HttpResponseBodyPart): STATE = STATE.ABORT
    }
    vs.formHttpClient
      .prepareGet(url.toString)
      .setHeader("Accept-Language", "en-us,en;q=0.5")
      .execute(handler)
    promise.future
  }

  def createAction: ActionA = AuthenticatedAction("form.createJob") { implicit req => user =>
    JobForm(user).bindFromRequest().fold(
      form => Future.successful {
        Metrics.form.createJobFailure()
        render {
          case Accepts.Html() => BadRequest(views.html.newJob(form, user))
          case Accepts.Json() => BadRequest
        }
      },
      job_ => {
        (for {
          newUrl <- checkEntrypoint(job_.strategy.entrypoint)
          job = job_.withEntrypoint(newUrl)
          _ <- job.save()
          _ <- job.run()
        } yield {
          render {
            case Accepts.Html() => SeeOther(routes.Jobs.index.url).flashing(("success" -> Messages("jobs.created", job.name)))
            case Accepts.Json() => Created(routes.Job.get(job.id).toString)
          }
        }) recover {
          case e: EntrypointException =>
            val form = JobForm(user).fill(job_.withEntrypoint(e.url)).withError("entrypoint", e.message, e.args: _*)
            BadRequest(views.html.newJob(form, user))
        }
      }
    )
  }

  def socket(typ: SocketType): Handler = {
    typ match {
      case SocketType.ws => webSocket()
      case SocketType.events => eventsSocket()
    }
  }

  def webSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    val enum: Enumerator[JsValue] = Enumerator.flatten(getUser().map(user => enumerator(user)))
    (iteratee, enum)
  }

  def eventsSocket: ActionA = AuthenticatedAction { implicit req => user =>
    render {
      case AcceptsStream() => Status(200).chunked(enumerator(user) &> EventSource()).as(MimeTypes.EVENT_STREAM)
    }
  }

  private def enumerator(user: User): Enumerator[JsValue] = {
    user.jobDatas() &> Enumeratee.map {
      iterator =>
        PlayJson.toJson(iterator.map(JobView(_).toJson))
    }
  }

}
