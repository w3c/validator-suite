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
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import play.api.data.FormError
import play.api.mvc.AsyncResult
import play.api.data.Forms._
import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.json._
import play.api.mvc.PathBindable

object Validator extends Controller with Secured {
  
  val logger = play.Logger.of("Controller.Validator")
  
  import org.w3.vs.prod.configuration
  
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
          Right(data isDefinedAt key)
        def unbind(key: String, value: Boolean): Map[String, String] =
          if (value) Map(key -> "on") else Map()
      })
    )
  )
  
  def index = IfAuth { _ => implicit user: User => Ok(views.html.index()) }
  
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
    val job = Job(strategy = strategy)
    
    logger.debug("job: "+job)
    
    val run = Run(job = job)
    
    logger.debug("run: "+run)

    val observer = configuration.observerCreator.observerOf(run)
    
    val runIdString: String = run.id.toString
    
    Created("").withHeaders(
      "Location" -> (request.uri + "/" + runIdString),
      "X-VS-ActionID" -> runIdString)
  }
  
  def validate() = IfAuth { implicit request => implicit user =>
    validateForm.bindFromRequest.fold(
      formWithErrors => { logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
      v => validateWithParams(request, v._1, v._2, v._3)
    )
  }
  
  def redirect(id: String) = IfAuth { implicit request => implicit user =>
    try {
      val runId: Run#Id = UUID.fromString(id)
      configuration.observerCreator.byRunId(runId).map { observer =>
        Redirect("/#!/observation/" + id)
      }.getOrElse(NotFound(views.html.index(Seq("Unknown action id: " + id))))
    } catch { case e =>
      NotFound(views.html.index(Seq("Invalid action id: " + id)))
    }
  }
  
  def stop(id: String) = IfAuth { implicit request => implicit user =>
    configuration.observerCreator.byRunId(id).map {o => /*o.stop();*/ Ok}.getOrElse(NotFound)
  }
  
  /*
   * Authenticated socket responsible for streaming the dashboard data, i.e. the list of 
   * jobs owned by the current user.
   
  def dashboardSocket: WebSocket[JsValue] = AuthenticatedWebSocket { username => request =>
    User.findByEmail(username).map { user =>
      // Get the list of jobs of the user
      val socket = (Iteratee.foreach[JsValue](e => println(e)))
      var enum = Enumerator.imperative[JsValue]()
      user.jobs.map { job =>
        // Subscribe a DashboardSubscriber to the job's observer
        val sub = job.observer.observerOf(new DashboardSubscriber())
        job.observer.subscribe2(sub)
        enum = enum >>> sub.enumerator
      }
      enum = enum &> Enumeratee.map[message.ObservationUpdate]{ e => e.toJS }
      (Iteratee.ignore, enum)
    }.getOrElse(CloseWebsocket)
  }
  */
  // def jobSocket
  // 
  def subscribe(id: String): WebSocket[JsValue] = AuthenticatedWebSocket { username => request =>
    configuration.observerCreator.byRunId(id).map { observer =>
      val in = Iteratee.foreach[JsValue](e => println(e))
      val enumerator = Subscribe.to(observer)
      (in, enumerator &> Enumeratee.map[message.ObservationUpdate]{ e => e.toJS })
    }.getOrElse(CloseWebsocket)
  }
  
  // def dashboardSocket()
  // Get user's list of jobs
  // for each job subscribe a dashboard subscriber
  
  
  // * Pages
  // login
  // logout
  // dashboard
  def dashboard = IfAuth { _ => implicit user => Ok(views.html.dashboard())}
  // job (report)
  // job/url (focus)
  
  // * Sockets
  // dashboardSocket
  // jobSocket
  // uriSocket
  
  // * Ajax actions
  // createJob
  // editJob
  // runJob
  // stopJob
  // deleteJob
  
}