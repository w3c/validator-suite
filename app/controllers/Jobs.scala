package controllers

import java.util.concurrent.TimeUnit.SECONDS

import scala.Option.option2Iterable

import org.w3.util.Pimps.wrapFuture
import org.w3.util.Pimps.wrapOption
import org.w3.util.Pimps.wrapValidation
import org.w3.util.DateTimeOrdering
import org.w3.util.FutureValidationNoTimeOut
import org.w3.vs.actor.Organization
import org.w3.vs.controllers.ec
import org.w3.vs.controllers.isAjax
import org.w3.vs.controllers.isValidForm
import org.w3.vs.controllers.jobForm
import org.w3.vs.exception.StoreException
import org.w3.vs.exception.SuiteException
import org.w3.vs.exception.UnauthorizedJob
import org.w3.vs.exception.Unexpected
import org.w3.vs.model.Assertions
import org.w3.vs.model.AssertorResult
import org.w3.vs.model.Context
import org.w3.vs.model.Job
import org.w3.vs.model.JobData
import org.w3.vs.model.JobId
import org.w3.vs.model.User
import org.w3.vs.view.AssertorHeader
import org.w3.vs.view.ContextHeader
import org.w3.vs.view.ContextValue
import org.w3.vs.view.Helper
import org.w3.vs.view.MessageHeader
import org.w3.vs.view.PageNav
import org.w3.vs.view.PositionValue
import org.w3.vs.view.ReportSection
import org.w3.vs.view.ReportValue
import org.w3.vs.view.UrlHeader

import Application.FutureTimeoutError
import Application.getAuthenticatedUser
import Application.getAuthenticatedUserOrResult
import Application.toResult
import akka.dispatch.Future
import play.api.i18n.Messages
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.mvc.Request
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.AsyncResult
import play.api.mvc.Controller
import play.api.mvc.WebSocket
import scalaz.Scalaz._
import scalaz._

object Jobs extends Controller {
  
  val logger = play.Logger.of("Controller.Jobs")
  // TODO: make the implicit explicit!!!
  implicit def configuration = org.w3.vs.Prod.configuration
  
  import Application._
  
  def redirect = Action { implicit req => Redirect(routes.Jobs.index) }
  
  def index = Action { implicit req =>
    AsyncResult {
      val futureResult = for {
        user <- getAuthenticatedUserOrResult
        jobs <- configuration.store.listJobs(user.organization).failMap(toResult(Some(user)))
        jobDatas <- {
          val futureJobDatas: Future[Iterable[JobData]] = Future.sequence(jobs map { _.jobData() })
          futureJobDatas.lift
        }.failMap(t => toResult(Some(user))(StoreException(t)))
      } yield {
        val sortedJobs = jobs.toSeq.sortBy(_.createdOn)
        val map: Map[JobId, JobData] = jobDatas.map{ jobData => (jobData.jobId, jobData) }.toMap
        val viewInputs = sortedJobs map { job => (job, map(job.id)) }
        Ok(views.html.dashboard(viewInputs, user))
      }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  def show(id: JobId, messages: List[(String, String)] = List.empty) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
          data <- job.jobData.lift failMap {t => toResult(Some(user))(Unexpected(t))}
          ars <- Job.getAssertorResults(job.id) failMap toResult(Some(user))
        } yield {
          val p = paginate(group(ars.collect{case a: Assertions => a}))
          Ok(views.html.job(job, data, p._1, p._2, user, messages))
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise()
    }
  }
  import org.w3.vs.view._
  private def group(ar: Iterable[Assertions])(implicit req: Request[AnyContent]): List[ReportSection] = {
    
    // (url, assertor, title, severity, context, line, column)
    type Data = List[(String, String, String, String, String, Option[Int], Option[Int])]
    
    val includeValidResult = req.queryString.get("valid").flatten.headOption match {case Some("on") => true; case _ => false}
    
    val flat: Data = (for {
        ass <- ar
        if (includeValidResult || !ass.isValid)
        raw <- ass.assertions
        context <- {if (raw.contexts.isEmpty) Seq(Context("", "", None, None)) else raw.contexts}
      } yield {
        (ass.url.toString, ass.assertorId.toString, raw.title, raw.severity, context.content, context.line, context.column)
      }).toList
      
    val filtered: Data = {
      val assertorsParams = req.queryString.get("assertor").flatten
      val typeParams = req.queryString.get("type").flatten
      (assertorsParams, typeParams) match {
        case (a, t) if (!a.isEmpty && !t.isEmpty) => flat.filter{a => assertorsParams.exists(_ === a._2) && typeParams.exists(_ === a._4)}
        case (a, _) if (!a.isEmpty) => flat.filter{a => assertorsParams.exists(_ === a._2) && List("error", "warning").exists(_ === a._4)}
        case (_, t) if (!t.isEmpty) => flat.filter{a => List("CSSValidator", "HTMLValidator", "I18n-Checker").exists(_ === a._2) && typeParams.exists(_ === a._4)}
        case _ => flat.filter{a => List("CSSValidator", "HTMLValidator", "I18n-Checker").exists(_ === a._2) && List("error", "warning").exists(_ === a._4)}
      }
    }
    
    def groupByMessage(f: => Data => Either[List[ReportValue], List[ReportSection]])(data: Data): Right[List[ReportValue], List[ReportSection]] = {
      Right(for {
        g <- data.groupBy(t => (t._2, t._3, t._4)).toList.sortBy(_._1._2).reverse.sortBy(_._1._3) //.sortBy(_._2.size).reverse
      } yield {
        val ((assertor, title, severity), iterable) = g
        ReportSection(MessageHeader(title, severity, assertor), f(iterable))
      })
    }
    def groupByContext(f: => Data => Either[List[ReportValue], List[ReportSection]])(data: Data): Right[List[ReportValue], List[ReportSection]] = {
      Right(for {
        g <- data.groupBy(t => (t._5)).toList.sortBy(_._1.length).reverse //.sortBy(_._2.size).reverse
        if (g._1 != "" || g._2.size != 0)
      } yield {
        val ((context), iterable) = g
        ReportSection(ContextHeader(context), f(iterable))
      })
    }
    def groupByAssertor(f: => Data => Either[List[ReportValue], List[ReportSection]])(data: Data): Right[List[ReportValue], List[ReportSection]] = {
      Right(for {
        g <- data.groupBy(t => (t._2)).toList.sortBy(_._1) //.sortBy(_._2.size).reverse
      } yield {
        val ((assertor), iterable) = g
        ReportSection(AssertorHeader(assertor), f(iterable))
      })
    }
    def groupByUrl(f: => Data => Either[List[ReportValue], List[ReportSection]])(data: Data): Right[List[ReportValue], List[ReportSection]] = {
      Right(for {
        g <- data.groupBy(t => (t._1)).toList.sortBy(_._1).reverse //.sortBy(_._2.size).reverse
      } yield {
        val ((url), iterable) = g
        ReportSection(UrlHeader(url), f(iterable))
      })
    }
    def getContextValues(data: Data): Left[List[ReportValue], List[ReportSection]] = {
      Left(data.map{case (_, _, _, _, context, line, column) => ContextValue(context, line, column)})
    }
    def getPositionValues(data: Data): Left[List[ReportValue], List[ReportSection]] = {
      Left(data.map{case (_, _, _, _, context, line, column) => PositionValue(line, column)})
    }
    def sortOnUrl(sections: List[ReportSection]): List[ReportSection] = {
      if (!sections.isEmpty) {
        sections(0).header match {
          case UrlHeader(url) => sections.sortBy(_.header match {case UrlHeader(url) => url; case _ => ""})
          case _ => sections.map{sect => ReportSection(sect.header, sect.list.fold(v => Left(v), s => Right(sortOnUrl(s))))}
        }
      } else sections
    }
    def sortOnOccurences(sections: List[ReportSection]): List[ReportSection] = {
      sections.sortBy(_.list.fold(
        values => values.size,
        sections => Helper.countValues(sections)
      )).map(section => ReportSection(section.header, section.list.fold(v => Left(v), s => Right(sortOnOccurences(s))))).reverse
    }
    
    val groupParam = req.queryString.get("group").flatten.headOption
    val grouped = groupParam match {
      case _@ (Some("message") | Some("message.url")) => {
        groupByMessage(groupByUrl(groupByContext(getPositionValues _)))(filtered).b
      }
      case Some("message.context") => {
        groupByMessage(groupByContext(groupByUrl(getPositionValues _)))(filtered).b
      }
      case _ => {
        groupByUrl(groupByAssertor(groupByMessage(getContextValues _)))(filtered).b
      }
    }
    
    val flatParam = req.queryString.get("flat").flatten.headOption
    val flatten = if (!flatParam.isDefined) grouped else {
      for {
        g <- grouped
        if g.list.isRight
        sub <- g.list.right.get
      } yield {
        ReportSection(g.header, Right(List(sub))) 
      }
    }
    
    val sortParam = req.queryString.get("sort").flatten.headOption
    val sorted = sortParam match {
      case Some("url") => sortOnUrl(sortOnOccurences(flatten)) 
      case _ => sortOnOccurences(flatten)
    }
    
    sorted
  }
  
  private def paginate(sections: List[ReportSection])(implicit req: Request[_]): (List[ReportSection], PageNav) = {
    val sectionsPerPage = 20
    val totalSections = sections.size
    val currentPage = req.queryString.get("p").flatten.headOption.getOrElse("1").toInt
    val totalPages = scala.math.ceil(totalSections.toFloat / sectionsPerPage.toFloat).toInt
    val paged = sections.slice((currentPage - 1) * sectionsPerPage, currentPage * sectionsPerPage)
    val nav = PageNav(currentPage, totalPages, totalSections)
    (paged, nav)
  }
  
  private def filter(ar: Iterable[AssertorResult])(implicit req: Request[AnyContent]): Iterable[Assertions] = {
    ar.collect{case a: Assertions => a}
  }
  
  // TODO: This should also stop the job and kill the actor
  def delete(id: JobId) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
          _ <- Job.delete(id) failMap toResult(Some(user))
        } yield {
          job.cancel
          if (isAjax) Ok else SeeOther(routes.Jobs.index.toString).flashing(("info" -> Messages("jobs.deleted", job.name)))
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  def new1 = newOrEditJob(None)
  def edit(id: JobId) = newOrEditJob(Some(id))
  def create = createOrUpdateJob(None)
  def update(id: JobId) = createOrUpdateJob(Some(id))
  
  def on(id: JobId) = simpleJobAction(id)(user => job => job.on())("jobs.on")
  def off(id: JobId) = simpleJobAction(id)(user => job => job.off())("jobs.off")
  def refresh(id: JobId) = simpleJobAction(id)(user => job => job.run())("jobs.refreshed")
  def stop(id: JobId) = simpleJobAction(id)(user => job => job.cancel())("jobs.stopped")
  
  def dispatcher(implicit id: JobId) = Action { implicit req =>
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
      case "refresh" => refresh(id)(req)
      case a => BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "unknown action " + a))))) // TODO Logging
    }).getOrElse(BadRequest(views.html.error(List(("error", Messages("debug.unexpected", "no action parameter was specified"))))))
  }
  
  def dashboardSocket() = WebSocket.using[JsValue] { implicit req =>

    val promiseEnumerator: Promise[Enumerator[JsValue]] = (
      for {
        user <- getAuthenticatedUser
      } yield {
        val organization = Organization(user.organization)
        val enumerator = organization.subscribeToUpdates()
        enumerator &> Enumeratee.map(_.toJS)
      }
    ).failMap(_ => Enumerator.eof[JsValue])
     .expiresWith(Enumerator.eof[JsValue], 3, SECONDS)
     .toPromiseT[(Enumerator[JsValue])]
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }

  /*
   * Private methods
   */
  private def newOrEditJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          id <- idOpt.toImmediateSuccess(ifNone = Ok(views.html.jobForm(jobForm, user )))
          jobC <- getJobIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          Ok(views.html.jobForm(jobForm.fill(jobC), user, idOpt))
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  private def createOrUpdateJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          jobF <- isValidForm(jobForm).toImmediateValidation failMap { formWithErrors =>
            BadRequest(views.html.jobForm(formWithErrors, user))
          }
          result <- idOpt match {
            case None =>
              for {
                _ <- Job.save(jobF.copy(creatorId = user.id, organizationId = user.organization)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> Messages("jobs.created", jobF.name))))) 
                else        SeeOther(routes.Jobs.show(jobF.id).toString).flashing(("info" -> Messages("jobs.created", jobF.name)))
              }
            case Some(id) =>
              for {
                jobC <- getJobIfAllowed(user, id) failMap toResult(Some(user))
                 _ <- Job.save(jobC.copy(strategy = jobF.strategy, name = jobF.name)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> Messages("jobs.updated", jobC.name))))) 
                else        SeeOther(routes.Jobs.show(jobC.id).toString).flashing(("info" -> Messages("jobs.updated", jobC.name)))
              }
          }
        } yield {
          result
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          action(user)(job)
          if (isAjax) Accepted(views.html.libs.messages(List(("info" -> Messages(msg, job.name))))) 
          else        SeeOther(routes.Jobs.show(job.id).toString).flashing(("info" -> Messages(msg, job.name)))
        }
      futureResult.expiresWith(FutureTimeoutError, 3, SECONDS).toPromise
    }
  }
  
  private def getJobIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, Job] = {
    for {
      job <- Job.get(id)
      jobAllowed <- {
        val validation = if (job.organizationId === user.organization) Success(job) else Failure(UnauthorizedJob)
        validation.toImmediateValidation
      }
    } yield jobAllowed
  }
  
}
