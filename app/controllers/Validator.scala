package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data.format.Formatter
import play.api.mvc.Request
import java.net.URI
import java.util.UUID
import org.w3.vs.{ValidatorSuiteConf, Production}
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.vs.observer.message._
import play.api.data.FormError
import play.api.mvc.AsyncResult
import play.api.data.Forms._

import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.libs.json._

import akka.dispatch._

object Validator extends Controller with Secured {
  
  implicit val configuration: ValidatorSuiteConf = new Production { }
  
  val logger = play.Logger.of("Controller.Validator")
  
  implicit val urlFormat = new Formatter[URL] {
    
    override val format = Some("format.url", Nil)
    
    def bind(key: String, data: Map[String, String]) = {
      stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[URL]
          .either(URL(s))
          .left.map(e => Seq(FormError(key, "error.url", Nil)))
      }
    }
    
    def unbind(key: String, url: URL) = Map(key -> url.toString)
  }
  
  val validateForm = Form(
    tuple(
      "url" -> of[URL],
      "distance" -> of[Int].verifying(min(0), max(10)),
      "linkCheck" -> of[Boolean](new Formatter[Boolean] {
        def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
          if (data.get(key) != None) Right(true) else Right(false)
        def unbind(key: String, value: Boolean): Map[String, String] =
          if (value) Map(key -> "on") else Map()
      })
    )
  )
  
  def index = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      Ok(views.html.index(Some(user)))
    }.getOrElse(
      //Redirect(routes.Application.login)
      Unauthorized
      //Forbidden
    )
  }
  
  def validateWithParams(
      request: Request[AnyContent],
      url: URL,
      distance: Int,
      linkCheck: Boolean) = {
    
    val strategy = EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="demo strategy",
      entrypoint=url,
      distance=distance,
      linkCheck=linkCheck,
      filter=Filter(include=Everything, exclude=Nothing))
    
    val job = Job(strategy)
    
    val observerId: ObserverId = ObserverId()
    
    val observer = configuration.observerCreator.observerOf(observerId, job)
    
    val observerIdString: String = observerId.toString
    
    Created("").withHeaders(
      "Location" -> (request.uri + "/" + observerIdString),
      "X-VS-ActionID" -> observerIdString)
  }
  
  def validate() = IsAuthenticated { username => implicit request =>
    User.findByEmail(username).map { user =>
      validateForm.bindFromRequest.fold(
        formWithErrors => { logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
        v => validateWithParams(request, v._1, v._2, v._3)
      )
    }.getOrElse(Forbidden)
  }
  
  def redirect(id: String) = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      try {
        val observerId = ObserverId(id)
        configuration.observerCreator.byObserverId(observerId).map { observer =>
          Redirect("/#!/observation/" + id)
        }.getOrElse(NotFound(views.html.index(Some(user), Seq("Unknown action id: " + observerId.toString))))
      } catch { case e =>
        NotFound(views.html.index(None, Seq("Invalid action id: " + id)))
      }
    }.getOrElse(Forbidden)
  }
  
  def stop(id: String) = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      try {
        val observerId = ObserverId(id)
        configuration.observerCreator.byObserverId(observerId).map { observer =>
          observer.stop()
          Ok
        }.getOrElse(NotFound)
      } catch { case e =>
        NotFound
      }
    }.getOrElse(Forbidden)
  }
  
  def subscribeWS(id: String) =
    WebSocket.using[JsValue]{ request => 
      try {
        val observerId = ObserverId(id)
        configuration.observerCreator.byObserverId(observerId).map { observer =>
          val in = Iteratee.foreach[JsValue](e => println(e))
          val subscriber = observer.subscriberOf(new SubscriberImpl(observer))
          (in, subscriber.enumerator &> Enumeratee.map[ObservationUpdate]{ e => e.toJS })
        }.getOrElse((Iteratee.foreach[JsValue](e => println(e)), Enumerator.eof))
      } catch { case e =>
        // TODO only catch correctly typed exception
        logger.error(e.getMessage(), e);
        (Iteratee.foreach[JsValue](e => println(e)), Enumerator.enumInput(Input.EOF))
      }
    }

}
