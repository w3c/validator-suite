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
import org.w3.vs.model.EntryPointStrategy
import akka.util.duration._
import akka.dispatch.Await
import akka.dispatch.Future
import play.api.Play.current
import play.libs.Akka._

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
  
  def index = IfAuth { _ => implicit user => Ok(views.html.index()) }
  
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
  
  def stop(id: String) = IfAuth { implicit request => implicit user =>
    configuration.runCreator.byJobId(id).map {o => /*o.stop();*/ Ok}.getOrElse(NotFound)
  }
  
  /*
   * Authenticated socket responsible for streaming the dashboard data, i.e. the list of 
   * jobs owned by the current user.
   
  def dashboardSocket: WebSocket[JsValue] = IfAuthSocket { request => user =>
    // Get the list of jobs of the user
    val socket = (Iteratee.foreach[JsValue](e => println(e)))
    var enum = Enumerator.imperative[JsValue]()
    user.jobs.map { job =>
    // Subscribe a DashboardSubscriber to the job's run
      val sub = job.run.runOf(new DashboardSubscriber())
      job.run.subscribe2(sub)
      enum = enum >>> sub.enumerator
    }
    enum = enum &> Enumeratee.map[message.ObservationUpdate]{ e => e.toJS }
    (Iteratee.ignore, enum)
  }*/
  
  // def jobSocket
  // 
  def subscribe(id: String): WebSocket[JsValue] = IfAuthSocket { request => user =>
    configuration.runCreator.byJobId(id).map { run: Run =>
//      val run = runOpt getOrElse sys.error("tom told me this should go away anyway")
      val in = Iteratee.foreach[JsValue](e => println(e))
      val enumerator = run.subscribeToUpdates()
      (in, enumerator &> Enumeratee.map[message.RunUpdate]{ e => e.toJS })
    }.getOrElse(CloseWebsocket)
  }
  
  // def dashboardSocket()
  // Get user's list of jobs
  // for each job subscribe a dashboard subscriber
  
  
  // * Pages
  // login
  // logout
  // dashboard
  def dashboard = IfAuth {_ => implicit user => 
    AsyncResult {
      val jobDatas = Future.sequence(user.jobs.values map { _.getData }).asPromise
      jobDatas orTimeout("timeout", 1, SECONDS) map {either => 
        either fold(
          data => Ok(views.html.dashboard(user.jobs.values zip data)), 
          b => Results.InternalServerError(b) // TODO
        )
      }
    }
  }
  
  // new job
  // job (report)
  // job/url (focus)
  
  def showJob(id: Job#Id) = IfAuth {_ => implicit user => Ok(views.html.job())} // find job from id, check read access, redirect to report
  
  // finds job from id, checks ownership, updates job, redirects to dashboard
  def updateJob(id: Job#Id) = (IfAuth, IfJob(id)) {implicit request => implicit user => job =>
    if (user.owns(job)) {
      jobForm.bindFromRequest.fold (
        formWithErrors => BadRequest(views.html.jobForm(formWithErrors, job.id)),
        newJob => {
          store.putJob(job.copy(strategy = newJob.strategy, name = newJob.name))
          store.saveUser(user.withJob(job.copy(strategy = newJob.strategy, name = newJob.name)))
          Redirect(routes.Validator.dashboard)
          //Redirect(routes.Validator.dashboard.toString, 301)
        }
      )
    } else {
      Redirect(routes.Validator.dashboard)
    }
  } 
  
  // finds job from id, checks ownership, deletes job, redirects to dashboard
  def deleteJob(id: Job#Id) = (IfAuth, IfJob(id)) {_ => implicit user => job =>
    if (user.owns(job)) {
      store.saveUser(user.copy(jobs = user.jobs - id)) // TODO Also remove the job from the store? (method missing)
      Redirect(routes.Validator.dashboard)
    } else {
      Redirect(routes.Validator.dashboard) // TODO Display an error
    }
  } 
  
  // finds job from id, checks ownership, shows pre-filled form
  def editJob(id: Job#Id) = (IfAuth, IfJob(id)) {req => implicit user => job =>
    if (user.owns(job))
      Ok(views.html.jobForm(jobForm.fill(job), job.id))
    else
      Ok(views.html.jobForm(jobForm.fill(job), job.id)) // TODO throw an error / redirect
  }
  
  // finds job from id, checks ownership, runs it
  def runJob(id: Job#Id) = (IfAuth, IfJob(id)) {req => implicit user => job =>
    if (user.owns(job)) {
      job.getRun().start()
      Redirect(routes.Validator.dashboard)
    } else
      Redirect(routes.Validator.dashboard) // TODO throw an error / redirect
  }
  
  // creates a job and redirects to the dashboard
  def createJob() = IfAuth {implicit req => implicit user => 
    jobForm.bindFromRequest.fold (
      formWithErrors => BadRequest(views.html.jobForm(formWithErrors, null)),
      job => {
        store.saveUser(user.withJob(job))
        store.putJob(job) // ?
        Redirect(routes.Validator.dashboard)
      }
    )}
  
  // shows the job creation form
  def newJob() = IfAuth {_ => implicit user => Ok(views.html.jobForm(jobForm, null))}
  
  val jobForm = Form(
    mapping (
      "name" -> text,
      "url" -> of[URL],
      "distance" -> of[Int],
      "linkCheck" -> of[Boolean](booleanFormatter)
    )((name, url, distance, linkCheck) => {
      // done the following so that it compiles, but this is crearly wrong
      val fakeUser = User.fake
      Job(
        name = name,
        organization = fakeUser.organization,
        creator = fakeUser.id,
        strategy = new EntryPointStrategy(
          name="irrelevantForV1",
          entrypoint=url,
          distance=distance,
          linkCheck=linkCheck,
          filter=Filter(include=Everything, exclude=Nothing)))
    })
    ((job: Job) => Some(job.name, job.strategy.seedURLs.head, job.strategy.distance, job.strategy.linkCheck))
  )
  
//  def jobDispatcher(id: Job#Id) = Action { request =>
//    request.body.asFormUrlEncoded
//    logger.error(request.body.toString)
//    
//  }
  
  // * Sockets
  // dashboardSocket
  // jobSocket
  // uriSocket
  
  // * Ajax actions
  // createJob
  // editJob
  // jobJob
  // stopJob
  // deleteJob
  
}