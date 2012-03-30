package controllers

import play.api._
import play.api.mvc._
import play.api.i18n._
import play.api.data.Form
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import scalaz._
import Scalaz._
import Validation._
import akka.dispatch.Await
import akka.dispatch.Future
import akka.util.Duration
import akka.util.duration._
import java.util.concurrent.TimeUnit._
import org.w3.util.Pimps._
import org.w3.vs.assertor.AssertorId

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
        jobConfs <- configuration.store.listJobs(user.organization).toDelayedValidation.failMap(t => toResult(Some(user))(StoreException(t)))
        jobDatas <- {
          val jobs: Iterable[Job] = jobConfs map { jobConf => Job(jobConf.organization, jobConf.id) }
          val futureJobDatas: Future[Iterable[JobData]] = Future.sequence(jobs map { _.jobData() })
          futureJobDatas.lift
        }.failMap(t => toResult(Some(user))(StoreException(t)))
      } yield {
        val sortedJobsConf = jobConfs.toSeq.sortBy(_.createdOn)
        val map: Map[JobId, JobData] = jobDatas.map{ jobData => (jobData.jobId, jobData) }.toMap
        val viewInputs = sortedJobsConf map { jobConf => (jobConf, map(jobConf.id)) }
        Ok(views.html.dashboard(viewInputs, user))
      }
      futureResult.expiresWith(FutureTimeoutError, 1, SECONDS).toPromise
    }
  }
  
  def show(id: JobId, messages: List[(String, String)] = List.empty) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          jobConf <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
          job = Job(jobConf)
          data <- job.jobData.lift failMap {t => toResult(Some(user))(Unexpected(t))}
          ars <- Job.getAssertorResults(jobConf.id) failMap toResult(Some(user))
        } yield {
          Ok(views.html.job(jobConf, data, group(sort(filter(ars))), user, messages))
        }
      futureResult.expiresWith(FutureTimeoutError, 10, SECONDS).toPromise()
    }
  }
  private def group(ar: Iterable[Assertions])(implicit req: Request[AnyContent]): Either[Iterable[Assertions], Iterable[((String, String, AssertorId), Iterable[(URL, Iterable[(Option[Int], Option[Int])])])]] = {
    val groupParam = req.queryString.get("group").flatten.headOption
    groupParam match {
      case Some("contexts") => {
        val foo = for {
          ass <- ar
          raw <- ass.assertions
          if raw.isError
          context <- raw.contexts
        } yield {
          ((context.content, raw.title, ass.assertorId), ass.url, (context.line, context.column))
        }
        // Iterable[(code, error, assertor), url, (line, column)]
        // group by context and sort by number of occurences
        val byContext = foo.groupBy(_._1).toList.sortBy{case (_, i) => i.size}
        // Iterable[(code, error, assertor), Iterable[((code, error, assertor), url, (line, column))]]
        val byContext2 = for {
          obj <- byContext
        } yield (obj._1, obj._2.map{case (_, a, b) => (a, b)})
        // Iterable[(code, error, assertor), Iterable[(url, (line, column))]]
        val groupByMessage = byContext2.map{case (obj, iterable) =>
          (obj, for {
            obj <- iterable.groupBy(_._1).toList.sortBy{_._1.toString}.sortBy{_._2.size}
          } yield (obj._1, obj._2.map{_._2}))
        }
        // Iterable[(code, error, assertor), Iterable[(url, Iterable[(line, column)])]]
        // ie:
        // Iterable[(
        //   (String, String, AssertorId),
        //   Iterable[(
        //     URL,
        //     Iterable[(Option[Int], Option[Int])]
        //   )]
        // )]
        Right(groupByMessage.reverse)
      }
      case _ => Left(ar.take(50))
    }
  }
  private def sort(ar: Iterable[Assertions])(implicit req: Request[AnyContent]) = {
    val sortParam = req.queryString.get("sort").flatten.headOption
    ar.toList.sortWith{(a, b) =>
      val perErrors = if (a.numberOfErrors === b.numberOfErrors) a.url.toString < b.url.toString else a.numberOfErrors > b.numberOfErrors
      val perWarnings = if (a.numberOfWarnings === b.numberOfWarnings) perErrors else a.numberOfWarnings > b.numberOfWarnings
      val perUrls = a.url.toString < b.url.toString
      sortParam match {
        case Some("errors") => perErrors
        case Some("warnings") => perWarnings
        case Some("urls") => perUrls
        case _ => perErrors
      }
    }
  }
  private def filter(ar: Iterable[AssertorResult])(implicit req: Request[AnyContent]): Iterable[Assertions] = {
    val assertions = ar.collect{case a: Assertions => a}.view
    val assertorsParams = req.queryString.get("assertor").flatten // eg Seq(HTMLValidator, CSSValidator)
    val validOnlyParam = req.queryString.get("valid").isDefined
    val result = if (assertorsParams.isEmpty) assertions else assertions.filter{a => assertorsParams.exists(_ === a.assertorId.toString)}
    val result2 = if (validOnlyParam) result.filter{_.isValid} else result.filter{!_.isValid}
    result2
  }
  
  // TODO: This should also stop the job and kill the actor
  def delete(id: JobId) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          jobConf <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
          _ <- Job.delete(id) failMap toResult(Some(user))
        } yield {
          val job = Job(jobConf)
          job.stop
          if (isAjax) Ok else SeeOther(routes.Jobs.index.toString).flashing(("info" -> Messages("jobs.deleted", jobConf.name)))
        }
      futureResult.expiresWith(FutureTimeoutError, 1, SECONDS).toPromise
    }
  }
  
  def new1 = newOrEditJob(None)
  def edit(id: JobId) = newOrEditJob(Some(id))
  def create = createOrUpdateJob(None)
  def update(id: JobId) = createOrUpdateJob(Some(id))
  
  def on(id: JobId) = simpleJobAction(id)(user => job => job.on())("jobs.on")
  def off(id: JobId) = simpleJobAction(id)(user => job => job.off())("jobs.off")
  def refresh(id: JobId) = simpleJobAction(id)(user => job => job.refresh())("jobs.refreshed")
  def stop(id: JobId) = simpleJobAction(id)(user => job => job.stop())("jobs.stopped")
  
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
    val seed = new UpdateData(null)

    val promiseIterateeEnumerator = (for {
      user <- getAuthenticatedUser
      jobConfs <- Job.getAll(user.organization)
    } yield {
      val enumerators: Iterable[PushEnumerator[message.RunUpdate]] = jobConfs map { jobConf => Job(jobConf).subscribeToUpdates() }
      val in = Iteratee.ignore[JsValue].mapDone[Unit]{_ => 
      	enumerators map { _.close } // This doesn't work: PushEnumerator:close() does not call the enumerator's onComplete method, possibly a bug.
      }
      val out = {
        enumerators.map {(enum: Enumerator[RunUpdate]) =>
          // Filter the enumerator, taking only the UpdateData messages
          enum &> Enumeratee.collect[RunUpdate] { case e: UpdateData => e } &>
            // Transform to a tuple (updateData, sameAsPrevious)
            Enumeratee.scanLeft[UpdateData]((seed, false)) {
              case ((prev, _), to) if to != prev => (to, false)
              case (_, to) => (to, true)
            }
          // Interleave the resulting enumerators and collect messages that are marked as changed
        }.reduce((e1, e2) => e1 >- e2) &>  Enumeratee.collect { case (a, false) => a.toJS }
      }
      (in, out)
    }).failMap(_ => (Iteratee.ignore[JsValue], Enumerator.eof[JsValue]))
      .expiresWith((Iteratee.ignore[JsValue], Enumerator.eof[JsValue]), 1, SECONDS)
      .toPromiseT[(Iteratee[JsValue, _], Enumerator[JsValue])]
    
    val enumerator: Enumerator[JsValue] = Enumerator.flatten(promiseIterateeEnumerator.map(_._2))
    val iteratee: Iteratee[JsValue, _] = Iteratee.flatten(promiseIterateeEnumerator.map(_._1))
    
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
          jobC <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          Ok(views.html.jobForm(jobForm.fill(jobC), user))
        }
      futureResult.expiresWith(FutureTimeoutError, 1, SECONDS).toPromise
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
                _ <- Job.save(jobF.assignTo(user)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> Messages("jobs.created", jobF.name))))) 
                else        SeeOther(routes.Jobs.show(jobF.id).toString).flashing(("info" -> Messages("jobs.created", jobF.name)))
              }
            case Some(id) =>
              for {
                jobC <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
                 _ <- Job.save(jobC.copy(strategy = jobF.strategy, name = jobF.name)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> Messages("jobs.updated", jobC.name))))) 
                else        SeeOther(routes.Jobs.show(jobF.id).toString).flashing(("info" -> Messages("jobs.updated", jobC.name)))
              }
          }
        } yield {
          result
        }
      futureResult.expiresWith(FutureTimeoutError, 1, SECONDS).toPromise
    }
  }
  
  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          jobConf <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          val job = Job(jobConf)
          action(user)(job)
          if (isAjax) Accepted(views.html.libs.messages(List(("info" -> Messages(msg, jobConf.name))))) 
          else        SeeOther(routes.Jobs.show(jobConf.id).toString).flashing(("info" -> Messages(msg, jobConf.name)))
        }
      futureResult.expiresWith(FutureTimeoutError, 1, SECONDS).toPromise
    }
  }

  private def getJobConfIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, JobConfiguration] = {
    for {
      jobConfO <- Job.get(id)
      jobConf <- jobConfO.toSuccess(UnknownJob).toImmediateValidation
      jobConfAllowed <- {
        val validation = if (jobConf.organization === user.organization) Success(jobConf) else Failure(UnauthorizedJob)
        validation.toImmediateValidation
      }
    } yield jobConfAllowed
  }
  
}
