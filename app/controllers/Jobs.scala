package controllers

import org.w3.banana._
import org.w3.vs.actor.message._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view.collection._
import org.w3.vs.view.form._
import org.w3.vs.view.model._
import play.Logger.ALogger
import play.api.i18n.Messages
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.{EventSource, Comet}
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

object Jobs extends VSController {
  
  val logger: ALogger = play.Logger.of("org.w3.vs.controllers.Jobs")
  
  //def redirect: ActionA = Action { implicit req => Redirect(routes.Jobs.index) }

  def index: ActionA = AuthAsyncAction { implicit req => user =>
    for {
      jobs_ <- user.getJobs()
      jobs <- JobsView(jobs_)
    } yield {
      if (isAjax) {
        Ok(jobs.bindFromRequest.toJson)
      } else {
        Ok(views.html.main(
            user = user,
            title = "Jobs - Validator Suite",
            style = "",
            script = "test",
            collections = Seq(jobs.bindFromRequest)
        )).withHeaders(("Cache-Control", "no-cache, no-store"))
      }
    }
  }

  /*def report(id: JobId, url: URL/*, messages: List[(String, String)] = List.empty*/): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job_ <- user.getJob(id)
        assertions_ <- job_.getAssertions().map(_.filter(_.url == url)) // TODO Empty = exception
        assertors = AssertorsView(assertions_)
        assertions = AssertionsView(assertions_).filterOn(assertors.firstAssertor).bindFromRequest
        resource = ResourcesView.single(url, assertions, job_.id)
      } yield {
        Ok(views.html.main(
          user = user,
          title = s"Report for ${Helper.shorten(url, 50)} - Validator Suite",
          style = "",
          script = "test",
          crumbs = Seq((job_.name, routes.Assertions.index(job_.id).toString), (Helper.shorten(url, 50), "")),
          collections = Seq(
            resource.withAssertions(assertions),
            assertors.withAssertions(assertions),
            assertions
          ))).withHeaders(("Cache-Control", "no-cache, no-store"))
      }) recover toError
    }
  }*/

  def get(id: JobId/*, messages: List[(String, String)] = List.empty*/): ActionA = Action { implicit req =>
    req.getQueryString("group") match {
      case Some("message") => Action { implicit req => Redirect(routes.Assertions.index(id)) }(req) //reportByAssertions_(id, messages)
      case _ =>               Action { implicit req => Redirect(routes.Resources.index(id)) }(req) //reportByResources_(id, messages)
    }
  }

  /*def reportByAssertions_(id: JobId, messages: List[(String, String)] = List.empty)(implicit req: Request[_]) = {
    AsyncResult {
      (for {
        user <- getUser
        job_ <- user.getJob(id)
        assertions_ <- job_.getAssertions()
        job <- JobsView.single(job_)

      } yield {
        if (isAjax) {
          val assertions = AssertionsView.grouped(assertions_).bindFromRequest
          Ok(assertions.toJson)
        } else {
          val assertors = AssertorsView(assertions_)
          val assertions = AssertionsView.grouped(assertions_).filterOn(assertors.firstAssertor).bindFromRequest
          Ok(views.html.main(
            user = user,
            title = s"""Report for job "${job_.name}" - By messages - Validator Suite""",
            style = "",
            script = "test",
            crumbs = Seq((job_.name, "")),
            collections = Seq(
              job.withAssertions(assertions),
              assertors.withAssertions(assertions),
              assertions
            ))).withHeaders(("Cache-Control", "no-cache, no-store"))
        }
      }) recover toError
    }
  }

  def reportByResources_(id: JobId, messages: List[(String, String)] = List.empty)(implicit req: Request[_]) = {
    AsyncResult {
      (for {
        user <- getUser
        job_ <- user.getJob(id)
        assertions_ <- job_.getAssertions()
        job <- JobsView.single(job_)
        resources = ResourcesView(assertions_, job_.id).bindFromRequest
      } yield {
        if (isAjax) {
          Ok(resources.toJson)
        } else {
          Ok(views.html.main(
            user = user,
            title = s"""Report for job "${job_.name}" - By resources - Validator Suite""",
            style = "",
            script = "test",
            crumbs = Seq((job_.name, "")),
            collections = Seq(
              job.withResources(resources),
              resources
            ))).withHeaders(("Cache-Control", "no-cache, no-store"))
        }
      }) recover toError
    }
  }

  def reportByAssertions(id: JobId, messages: List[(String, String)] = List.empty): ActionA =
    Action { implicit req => reportByAssertions_(id, messages) }

  def reportByResources(id: JobId, messages: List[(String, String)] = List.empty): ActionA =
    Action { implicit req => reportByResources_(id, messages) }*/

  def delete(id: JobId): ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job <- user.getJob(id)
      _ <- job.delete()
    } yield {
      if (isAjax) Ok else SeeOther(routes.Jobs.index.toString) /*.flashing(("success" -> Messages("jobs.deleted", job.name)))*/
    }
  }
    
  def new1: ActionA = newOrEditJob(None)
  def edit(id: JobId): ActionA = newOrEditJob(Some(id))
  def create: ActionA = createOrUpdateJob(None)
  def update(id: JobId): ActionA = createOrUpdateJob(Some(id))
  
  def on(id: JobId): ActionA = simpleJobAction(id)(user => job => job.on())("jobs.on")
  def off(id: JobId): ActionA = simpleJobAction(id)(user => job => job.off())("jobs.off")
  def run(id: JobId): ActionA = simpleJobAction(id)(user => job => job.run())("jobs.run")
  def stop(id: JobId): ActionA = simpleJobAction(id)(user => job => job.cancel())("jobs.stop")
  
  def dispatcher(implicit id: JobId): ActionA = Action { implicit req =>
    (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "delete" => delete(id)(req)
      case "update" => update(id)(req)
      case "on" => on(id)(req)
      case "off" => off(id)(req)
      case "stop" => stop(id)(req)
      case "run" => run(id)(req)
      case a => BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "unknown action " + a))))) // TODO Logging
    }).getOrElse(BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "no action parameter was specified"))))))
  }

  private def enumerator(implicit reqHeader: RequestHeader): Enumerator[JsValue] = {
    Enumerator.flatten((
      for {
        user <- getUser
        org <- user.getOrganization() map (_.get)
      } yield {
        // ready to explode...
        // better: a user can belong to several organization. this would handle the case with 0, 1 and > 1
        org.enumerator &> Enumeratee.collect {
          case a: UpdateData => JobView.toJobMessage(a.jobId, a.data, a.activity)
          case a: RunCompleted => JobView.toJobMessage(a.jobId, a.completedOn)
        }
      }).recover{ case _ => Enumerator.eof[JsValue] })
  }

  def webSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit reqHeader =>
    val iteratee = Iteratee.ignore[JsValue]
    (iteratee, enumerator)
  }

  def cometSocket: ActionA = Action { implicit req =>
    Ok.stream(enumerator &> Comet(callback = "parent.VS.jobupdate"))
  }

  def eventsSocket: ActionA = Action { implicit req =>
    Ok.stream(enumerator &> EventSource()).as("text/event-stream")
  }
  
  /*def reportSocket(id: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit req =>
    val promiseEnumerator: Future[Enumerator[JsValue]] =
      (for {
        user <- getUser
        job <- user.getJob(id)
      } yield {
        job.enumerator &> Enumeratee.collect {
          case a: UpdateData => JobView.toJobMessage(a.jobId, a.data, a.activity)
          case a: RunCompleted => JobView.toJobMessage(a.jobId, a.completedOn)
          //case NewResource(resource) => ResourceUpdate.json(resource)
          //case NewAssertions(assertionsC) if (assertionsC.count(_.assertion.severity == Warning) != 0 || assertionsC.count(_.assertion.severity == Error) != 0) => AssertorUpdate.json(assertionsC)
          //case NewAssertorResult(result, datetime) if (!result.isValid) => AssertorUpdate.json(result, datetime)
        }
      }) recover { case __ => Enumerator.eof[JsValue] }
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }*/

  /*
   * Private methods
   */
  private def newOrEditJob(implicit idOpt: Option[JobId]): ActionA = AuthAsyncAction { implicit req => user =>
      (for {
        org <- user.getOrganization() map (_.get)
        form <- idOpt match {
          case Some(id) => user.getJob(id) map JobForm.fill _
          case None => JobForm.blank.asFuture
        }
      } yield {
        Ok(views.html.jobForm(form, user, org, idOpt))
      }) recover toError
    }

  private def createOrUpdateJob(implicit idOpt: Option[JobId]): ActionA = AuthAsyncAction { implicit req => user =>
    (for {
      org <- user.getOrganization() map (_.get)
      form <- JobForm.bind map {
        case Left(form) => throw new InvalidJobFormException(form, user, org, idOpt)
        case Right(validJobForm) => validJobForm
      }
      jobM <- idOpt match {
        case Some(id) => user.getJob(id)
          .flatMap(j => form.update(j)
          .save()
          .map(job => (job, "jobs.updated")))
        case None => form
          .createJob(user)
          .save()
          .map(job => (job, "jobs.created"))
      }
    } yield {
      val (job, msg) = jobM
      if (isAjax)
        Created(views.html.libs.messages(List(("success" -> Messages(msg, job.name)))))
      else
        SeeOther(routes.Jobs.index.toString /*show(job.id)*/) /*.flashing(("success" -> Messages(msg, job.name)))*/
    }) recover {
      case InvalidJobFormException(form, user, org, idOpt) => BadRequest(views.html.jobForm(form, user, org, idOpt))
    }
  }

  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String): ActionA = AuthAsyncAction { implicit req => user =>
    for {
      job <- user.getJob(id)
    } yield {
      action(user)(job)
      if (isAjax) {
        Accepted(views.html.libs.messages(List(("success" -> Messages(msg, job.name)))))
      } else {
        (for {
          body <- req.body.asFormUrlEncoded
          param <- body.get("uri")
          uri <- param.headOption
        } yield uri) match {
          case Some(uri) => SeeOther(uri) /*.flashing(("success" -> Messages(msg, job.name)))*/ // Redirect to "uri" param if specified
          case None =>SeeOther(routes.Resources.index(job.id).toString)
        }
      }
    }
  }

}
