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
import org.w3.vs.{ValidatorSuiteConf, Production}
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import play.api.data.FormError
import play.api.mvc.AsyncResult
import play.api.data.Forms._

import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

object Validator extends Controller with Secured {
  
  implicit val configuration: ValidatorSuiteConf = new Production { }
  
  val logger = play.Logger.of("Controller.Validator")
  
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
          if (data.get(key) != None) Right(true) else Right(false)
        def unbind(key: String, value: Boolean): Map[String, String] =
          if (value) Map(key -> "on") else Map()
      })
    )
  )
  
  def index = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      Ok(views.html.index(Some(user)))
    }.getOrElse(
      //Redirect(routes.Application.login)
      Unauthorized
      //Forbidden
    )
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
  
  def validate() = IsAuthenticated { username => implicit request =>
    User.findByEmail(username).map { user =>
      validateForm.bindFromRequest.fold(
        formWithErrors => { logger.error(formWithErrors.errors.toString); BadRequest(formWithErrors.toString) },
        v => validateWithParams(request, v._1, v._2, v._3)
      )
    }.getOrElse{ Forbidden }
  }
  
  private def toJSON(msg: message.ObservationUpdate): String = msg match {
    case message.Done => """["OBS_FINISHED"]"""
    case message.Stopped => """["STOPPED"]"""
    case message.NewAssertion(a) => a.result match {
      case AssertionError(why) => """["OBS_ERR", "%s"]""" format a.url
      case events@Events(_) => """["OBS", "%s", "%s", %d, %d]""" format (a.url, a.assertorId, events.errorsNumber, events.warningsNumber)
    }
    case message.NewResourceInfo(ri) => ri.result match {
      case ResourceInfoError(why) => """["ERR", "%s", "%s"]""" format (why, ri.url)
      case Fetch(status, headers, extractedLinks) => ri.action match {
        case GET => """["GET", %d, "%s", %d]""" format (status, ri.url, 0)
        case HEAD => """["HEAD", %d, "%s"]""" format (status, ri.url)
        case _ => sys.error("TODO you should change the type :-)")
      }
    }
    case message.ObservationSnapshot(messages) => {
      val initial = """["OBS_INITIAL", %d, %d, %d, %d]""" format (0, 0, 0, 0)
      val initialMessages = messages map toJSON mkString ""
      initial + initialMessages
    }
  }
  
  def redirect(id: String) = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      try {
        val runId: Run#Id = UUID.fromString(id)
        configuration.observerCreator.byRunId(runId).map { observer =>
          Redirect("/#!/observation/" + id)
        }.getOrElse(NotFound(views.html.index(Some(user), Seq("Unknown action id: " + id))))
      } catch { case e =>
        NotFound(views.html.index(None, Seq("Invalid action id: " + id)))
      }
    }.getOrElse(Forbidden)
  }
  
  def stop(id: String) = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      try {
        val runId: Run#Id = UUID.fromString(id)
        configuration.observerCreator.byRunId(runId).map { observer =>
          observer.stop()
          Ok
        }.getOrElse(NotFound)
      } catch { case e =>
        NotFound
      }
    }.getOrElse(Forbidden)
  }
  
  def subscribe(id: String) = IsAuthenticated { username => _ =>
    User.findByEmail(username).map { user =>
      try {
        val runId: Run#Id = UUID.fromString(id)
        configuration.observerCreator.byRunId(runId).map { observer =>
          AsyncResult {
            val subscriber = observer.subscriberOf(new SubscriberImpl(observer))
            val iteratee = subscriber.enumerator &> Enumeratee.map{ e => toJSON(e) } &> Comet(callback = "parent.VS.logComet")
            Promise.pure(Ok.stream(iteratee).withHeaders("X-VS-ActionID" -> id))
          }
        }.getOrElse(NotFound(views.html.cometError(Seq("Unknown action id: " + id))))
      } catch { case e =>
        // TODO only catch correctly typed exception
        logger.error(e.getMessage(), e);
        NotFound(views.html.cometError(Seq("Invalid action id: " + id)))
      }
    }.getOrElse(Forbidden)
  }
  
}
