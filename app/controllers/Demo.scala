package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data.format.Formatter
import play.api.mvc.Request
import play.api.data.FormError
import play.api.mvc.AsyncResult
import play.api.data.Forms._
import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.mvc.PathBindable
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit._
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.run._
import org.w3.vs.controllers._
import org.w3.vs.run.message._
import org.w3.vs.model.EntryPointStrategy
import akka.util.duration._
import akka.dispatch.Await
import akka.dispatch.Future
import play.api.Play.current
import play.libs.Akka._
import com.google.common.eventbus.Subscribe

// This will eventually go away
object Demo extends Controller {
  
  def store = org.w3.vs.Prod.configuration.store
  
  val logger = play.Logger.of("Controller.Validator")
  
  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration
  
  val validateForm = Form(
    tuple(
      "url" -> of[URL],
      "distance" -> of[Int].verifying(min(0), max(10)),
      "linkCheck" -> of[Boolean](booleanFormatter)
    )
  )
  
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

    logger.debug("jbefore")
    
    // this should be persisted
    val fakeUser = User.fake
    val job = Job(name = "unknown", organization = fakeUser.organization, creator = fakeUser.id, strategy = strategy)
    
    logger.debug("job: "+job)
    
    val run = configuration.runCreator.runOf(job)
    run.start()
    
    val jobIdString: String = job.id.toString
    
    Created("").withHeaders(
      "Location" -> (request.uri + "/" + jobIdString),
      "X-VS-ActionID" -> jobIdString)
  }
  
  def index = IfAuth { _ => implicit user => Ok(views.html.index()) }
  
  def validate() = IfAuth { implicit request => implicit user =>
    validateForm.bindFromRequest.fold(
      formWithErrors => { logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
      v => validateWithParams(request, v._1, v._2, v._3)
    )
  }
  
  def stop(id: String) = IfAuth { implicit request => implicit user =>
    configuration.runCreator.byJobId(id).map {o => /*o.stop();*/ Ok}.getOrElse(NotFound)
  }
  
  def redirect(id: String) = IfAuth { implicit request => implicit user =>
    try {
      val jobId: Job#Id = UUID.fromString(id)
      configuration.runCreator.byJobId(jobId).map { run =>
        Redirect("/#!/observation/" + id)
      }.getOrElse(NotFound(views.html.index(Seq("Unknown action id: " + id))))
    } catch { case e =>
      NotFound(views.html.index(Seq("Invalid action id: " + id)))
    }
  }
  
  def subscribe(id: String): WebSocket[JsValue] = IfAuthSocket { request => user =>
    configuration.runCreator.byJobId(id).map { run: Run =>
      val in = Iteratee.foreach[JsValue](e => println(e))
      val enumerator = run.subscribeToUpdates()
      (in, enumerator &> Enumeratee.map[message.RunUpdate]{ e => e.toJS })
    }.getOrElse(CloseWebsocket)
  }
  
}