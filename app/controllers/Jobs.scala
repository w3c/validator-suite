package controllers

import java.util.concurrent.TimeUnit.SECONDS

import org.w3.util._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.view._
import org.w3.vs.actor.message._

import Application._
import akka.dispatch.Future
import akka.util.duration._
import play.api.i18n.Messages
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumeratee
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.mvc._
import scalaz.Scalaz._
import scalaz._

object Jobs extends Controller {
  
  type ActionA = Action[AnyContent]
  
  val logger = play.Logger.of("org.w3.vs.controllers.Jobs")
  // TODO: make the implicit explicit!!!
  implicit def configuration = org.w3.vs.Prod.configuration
  
  import Application._
  
  def redirect: ActionA = Action { implicit req => Redirect(routes.Jobs.index) }
  
  def index: ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        jobs <- user.getJobs
        triples <- FutureVal.sequence(
            jobs.toSeq.sortBy(_.name).map(job => 
              for {
                run <- job.getRun()
                lastCompleted <- job.getLastCompleted()
              } yield (job, run, lastCompleted)
            ))
      } yield {
        Ok(views.html.dashboard(triples, user))
      }) failMap toError toPromise
    }
  }
  
  def show(id: JobId, messages: List[(String, String)] = List.empty): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
        run <- job.getRun()
        lastCompleted <- job.getLastCompleted()
        ars <- run.getURLArticles()
      } yield {
        Ok(views.html.report(job, run, lastCompleted, ars.map(URLArticle.apply _), user, messages))
      }) failMap toError toPromise
    }
  }
  
  def report(id: JobId, url: URL, messages: List[(String, String)] = List.empty): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
        run <- job.getRun()
        article <- run.getURLArticle(url) map URLArticle.apply _
        assertors <- run.getAssertorArticles(url) map {_.map(AssertorArticle.apply _)}
        assertions <- run.getAssertions(url) map (_.filter(_.severity != Info))
        tuples <- FutureVal.sequence(
            assertions.map(assertion => 
              for {
                contexts <- assertion.getContexts
              } yield (assertion, contexts)
            ))
      } yield {
        Ok(views.html.urlReport(job, article, assertors, tuples, user, messages))
      }) failMap toError toPromise
    }
  }
  
  /*
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
  */
    
  def delete(id: JobId): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
        _ <- job.delete()
      } yield {
        if (isAjax) Ok else SeeOther(routes.Jobs.index.toString).flashing(("info" -> Messages("jobs.deleted", job.name)))
      }) failMap toError toPromise
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
  
  def dashboardSocket(): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit req =>
    val promiseEnumerator: Promise[Enumerator[JsValue]] = (
      for {
        user <- getUser
        organization <- user.getOrganization
      } yield {
        organization.enumerator &> Enumeratee.collect{
          case a: UpdateData => JobsUpdate.json(a.data, a.activity)
        }
      }
    ) failMap (_ => Enumerator.eof[JsValue]) toPromise
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }
  
  def reportSocket(id: JobId): WebSocket[JsValue] = WebSocket.using[JsValue] { implicit req =>
    val promiseEnumerator: Promise[Enumerator[JsValue]] = (
      for {
        user <- getUser
        job <- user.getJob(id)
      } yield {
        job.enumerator &> Enumeratee.collect{
          case a: UpdateData => JobsUpdate.json(a.data, a.activity)
          //case NewResource(resource) => ResourceUpdate.json(resource)
          case NewAssertions(assertions) if (assertions.count(_.severity == Warning) != 0 || assertions.count(_.severity == Error) != 0) => AssertorUpdate.json(assertions)
        }
      }
    ) failMap (_ => Enumerator.eof[JsValue]) toPromise
    
    val iteratee = Iteratee.ignore[JsValue]
    val enumerator =  Enumerator.flatten(promiseEnumerator)

    (iteratee, enumerator)
  }

  /*
   * Private methods
   */
  private def newOrEditJob(implicit idOpt: Option[JobId]): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        form <- idOpt fold (
            id => user.getJob(id) map JobForm.fill _,
            FutureVal.successful(JobForm.blank)
          )
      } yield {
        Ok(views.html.jobForm(form, user, idOpt))
      }) failMap toError toPromise
    }
  }
  
  private def createOrUpdateJob(implicit idOpt: Option[JobId]): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        form <- JobForm.bind failMap (form => InvalidJobFormException(form, user, idOpt))
        job <- idOpt.fold(
            id => user.getJob(id).flatMap[Exception, Job](j => form.update(j).save()),
            form.createJob(user).save()
          )
      } yield {
        if (isAjax) 
          Created(views.html.libs.messages(List(("info" -> Messages("jobs.created", job.name))))) 
        else
          SeeOther(routes.Jobs.show(job.id).toString).flashing(("info" -> Messages("jobs.updated", job.name)))
      }) failMap {
        case InvalidJobFormException(form, user, idOpt) => BadRequest(views.html.jobForm(form, user, idOpt))
        case t => toError(t)
      } toPromise
    }
  }
  
  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String): ActionA = Action { implicit req =>
    AsyncResult {
      (for {
        user <- getUser
        job <- user.getJob(id)
      } yield {
        action(user)(job)
        if (isAjax) Accepted(views.html.libs.messages(List(("info" -> Messages(msg, job.name))))) 
        else        SeeOther(routes.Jobs.show(job.id).toString).flashing(("info" -> Messages(msg, job.name)))
      }) failMap toError toPromise
    }
  }
  
}
