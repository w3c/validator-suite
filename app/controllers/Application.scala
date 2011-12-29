package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data.format.Formatter
import java.net.URI
import java.util.UUID
import akka.actor.{ActorRef, Actor, Scheduler, UntypedChannel, TypedActor}
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import play.api.data.FormError

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index())
  }
  
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
  
  def validateWithParams(
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
    
    Created("").withHeaders(
      "Location" -> new URI(observerIdString).toString,
      "X-VS-ActionID" -> observerIdString)
  }
  
  def validate() = Action { implicit request =>
    validateForm.bindFromRequest.fold(
      formWithErrors => { Logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
      v => validateWithParams(v._1, v._2, v._3)
    )
    
    
    
  }
  
}