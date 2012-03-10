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

object Validator extends Controller {
  
  def store = org.w3.vs.Prod.configuration.store
  
  val logger = play.Logger.of("Controller.Validator")
  
  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration
  
  implicit def ec = configuration.webExecutionContext
  
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
  
  implicit val booleanFormatter = new Formatter[Boolean] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
      Right(data isDefinedAt key)
    def unbind(key: String, value: Boolean): Map[String, String] =
      if (value) Map(key -> "on") else Map()
  }
  
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

  // ------
  
  def jobForm(user: User) = Form(
    mapping (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter)
    )((name, url, distance, linkCheck) => {
      Job(
        name = name,
        organization = user.organization,
        creator = user.id,
        strategy = new EntryPointStrategy(
          name="irrelevantForV1",
          entrypoint=url,
          distance=distance,
          linkCheck=linkCheck,
          filter=Filter(include=Everything, exclude=Nothing)))
    })
    ((job: Job) => Some(job.name, job.strategy.seedURLs.head, job.strategy.distance, job.strategy.linkCheck))
  )

  // * Indexes
  def index = IfAuth { _ => implicit user => Ok(views.html.index()) }
  
  def dashboard = IfAuth {_ => implicit user =>
    AsyncResult {
      val jobs = store.listJobs(user.organization).fold(t => throw t, jobs => jobs)
      val jobDatas = jobs map ( _.getData )
      val foo = Future.sequence(jobDatas).asPromise orTimeout("timeout", 1, SECONDS)
      foo map {either => 
        either fold(
          data => Ok(views.html.dashboard(jobs zip data)), 
          b => Results.InternalServerError(b) // TODO
        )
      }
    }
  }
  
  // * Jobs
  def newJob() = IfAuth {_ => implicit user => Ok(views.html.jobForm(jobForm(user)))}
  
  def createJob() = IfAuth {implicit req => implicit user => 
    jobForm(user).bindFromRequest.fold (
      formWithErrors => BadRequest(views.html.jobForm(formWithErrors)),
      job => {
        store.putJob(job.copy(creator = user.id, organization = user.organization)) // ?
        Redirect(routes.Validator.dashboard)
      }
    )
  }
  
  def jobDispatcher(id: Job#Id) = Action { request =>
    var s = for {
      body <- {logger.error(request.body.asFormUrlEncoded.toString); request.body.asFormUrlEncoded}
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "update" => updateJob(id)(request)
      case "delete" => deleteJob(id)(request)
      case "run" => runJob(id)(request)
      case "stop" => stopJob(id)(request)
    }
    s.getOrElse(BadRequest) // TODO error with flash
    // Can i do that in one expression?
  }
  
  def updateJob(id: Job#Id) = (IfAuth, IfJob(id)) {implicit request => implicit user => job =>
    if (user.owns(id)) {
      jobForm(user).bindFromRequest.fold (
        formWithErrors => BadRequest(views.html.jobForm(formWithErrors, Some(job))),
        newJob => {
          store.putJob(job.copy(strategy = newJob.strategy, name = newJob.name))
          Redirect(routes.Validator.dashboard)
          //Redirect(routes.Validator.dashboard.toString, 301)
        }
      )
    } else {
      Redirect(routes.Validator.dashboard)
    }
  } 
  
  def deleteJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      store.removeJob(id)
      if (isAjax) Ok else Redirect(routes.Validator.dashboard)
    } else {
      if (isAjax) InternalServerError else Redirect(routes.Validator.dashboard)// TODO error
    }
  }
  
  def runJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      job.getRun().start()
      if (isAjax) Ok else Redirect(routes.Validator.dashboard)
    } else
      if (isAjax) InternalServerError else Redirect(routes.Validator.dashboard)// TODO error
  }
  
  def stopJob(id: Job#Id) = (IfAuth, IfJob(id), IsAjax) {_ => implicit user => job => isAjax =>
    if (user.owns(id)) {
      // TODO
      if (isAjax) Ok else Redirect(routes.Validator.dashboard)
    } else {
      if (isAjax) InternalServerError else Redirect(routes.Validator.dashboard)// TODO error
    }
  }
  
  def showJob(id: Job#Id) = IfAuth {_ => implicit user => Ok(views.html.job())}
  
  def editJob(id: Job#Id) = (IfAuth, IfJob(id)) {req => implicit user => job =>
    if (user.owns(id))
      Ok(views.html.jobForm(jobForm(user).fill(job), Some(job)))
    else
      Ok(views.html.jobForm(jobForm(user).fill(job), Some(job))) // TODO error
  }
  
  // * Sockets
  def dashboardSocket() = IfAuthSocket {req => user =>
    val in = Iteratee.foreach[JsValue](e => println(e)) // TODO unsubscribe on client close
    val jobs = store.listJobs(user.organization).fold(t => throw t, jobs => jobs)
    var out = jobs.map(_.getRun().subscribeToUpdates)
      .reduce((e1, e2) => e1 >- e2) &>
      Enumeratee.collect{case e: UpdateData => e.toJS}
    (in, out)
  }
  // jobSocket
  // uriSocket
  
}