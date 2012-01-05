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
import akka.actor.{ActorRef, Actor, Scheduler, UntypedChannel, TypedActor}
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import play.api.data.FormError
import play.api.mvc.AsyncResult

import play.api.libs._
import play.api.libs.iteratee._

import play.api.libs.concurrent._
import play.api.libs.akka._

object Application extends Controller {
  
  val logger = play.Logger.of("Application")
  
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
    of(
      "url" -> of[URL],
      "distance" -> of[Int].verifying(min(0), max(10)),
      "linkCheck" -> of[Boolean]
    )
  )
  
  def index = Action {
    Ok(views.html.index())
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
    
    val observerId: ObserverId = ObserverId()
    
    val observer = Observer.newObserver(observerId, strategy)
    
    observer.startExplorationPhase()
    
    val observerIdString: String = observerId.toString
    
    logger.error(request.uri)
    
    Created("").withHeaders(
      "Location" -> (request.uri + "/" + observerIdString),
      "X-VS-ActionID" -> observerIdString)
  }
  
  def validate() = Action { implicit request =>
    validateForm.bindFromRequest.fold(
      formWithErrors => { Logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
      v => validateWithParams(request, v._1, v._2, v._3)
    )
  }
  
  def redirect(id: String) = Action {
    try {
      val observerId = ObserverId(id)
      Observer.byObserverId(observerId).map { observer =>
        Redirect("/#!/observation/" + id)
      }.getOrElse(NotFound(views.html.index(Seq("Unknown action id: " + observerId.toString))))
    } catch { case e =>
      NotFound(views.html.index(Seq("Invalid action id: " + id)))
    }
  }
  
  def subscribe(id: String) = Action {
    try {
      val observerId = ObserverId(id)
      Observer.byObserverId(observerId).map { observer =>
        AsyncResult {
          val ce = new CallbackEnumerator[String] { }
          val subscriber = TypedActor.newInstance(classOf[ObserverSubscriber], new Subscriber(ce, observer)) //new Subscriber(ce, observer)
          Promise.pure(Ok.stream(ce &> Enumeratee.map{ e => logger.error(e.toString); e } &> Comet(callback = "parent.VS.logComet")))
        }
      }.getOrElse(NotFound(views.html.cometError(Seq("Unknown action id: " + observerId.toString))))
    } catch { case e =>
      NotFound(views.html.cometError(Seq("Invalid action id: " + id)))
    }
  }
}

class Subscriber(
    callback: CallbackEnumerator[String],
    observer: Observer)
    extends TypedActor with ObserverSubscriber {
  
  subscribe()
  
  // subscribes to the actionManager at instantiation time
  def subscribe(): Unit = observer.subscribe(this)
  
  def unsubscribe(): Unit = observer.unsubscribe(this)
  
  def broadcast(msg: String): Unit = {
    callback.push(msg)
  }
  
}
