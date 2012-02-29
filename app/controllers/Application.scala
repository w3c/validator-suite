package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import play.api.data.format.Formatter
import play.api.mvc.{AsyncResult, Request}
import play.api.data.Forms._
import java.net.URI
import java.util.UUID
import org.w3.util._
import org.w3.vs.model._
import org.w3.vs.observer._
import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.json.JsValue
import play.api.mvc.WebSocket.FrameFormatter
import scala.PartialFunction
import org.w3.vs.prod.configuration.store

object Application extends Controller {
  
  val logger = play.Logger.of("Controller.Application")
  
  // -- Authentication

  val loginForm = Form(
    tuple(
      "email" -> text,
      "password" -> text
    ) verifying ("Invalid email or password", result => result match {
      case (email, password) => store.authenticate(email, password).fold(t => false, _.isDefined)
    })
  )

  /**
   * Login page.
   */
  def login = IfNotAuth { 
    implicit request => Ok(views.html.login(loginForm))
  }{
    implicit request => Redirect(routes.Validator.index)
  }

  /**
   * Handle login form submission.
   */
  def authenticate = Action { implicit request =>
    loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.login(formWithErrors)),
      user => Redirect(routes.Validator.index).withSession("email" -> user._1)
    )
  }

  /**
   * Logout and clean the session.
   */
  def logout = Action {
    Redirect(routes.Application.login).withNewSession.flashing(
      "success" -> "You've been logged out"
    )
  }
}

/**
 * Provide security features
 */
trait Secured {
  
  /**
   * Retrieve the connected user email.
   */
  private def username(request: RequestHeader) = request.session.get("email")
//
//  /**
//   * Redirect to login if the user in not authorized.
//   */
//  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)
//  
  /**
   * Simply close the websocket if the user is not authorized.
   */
  private def onUnauthorizedWebSocket[A](request: RequestHeader) = CloseWebsocket[A]
  protected def CloseWebsocket[A]: (Iteratee[A, _], Enumerator[A]) = (Iteratee.foreach[A](e => println(e)), Enumerator.eof)
//  
//  /** 
//   * Action for authenticated users.
//   */
//  def IsAuthenticated(f: => String => Request[AnyContent] => Result): Action[(Action[AnyContent], AnyContent)] = Security.Authenticated(username, onUnauthorized) { user =>
//    Action(request => f(user)(request))
//  }
  
  /** 
   * WebSocket for authenticated users.
   */
  def AuthenticatedWebSocket[A](f: => String => RequestHeader => (Iteratee[A,_], Enumerator[A]))(implicit frameFormatter: FrameFormatter[A]): WebSocket[A] = {
    WebSocket.using[A](request => {
      username(request).map { user =>
        f(user)(request)
      }.getOrElse {
        onUnauthorizedWebSocket(request)
      }
    })
  }
  
  /**
   * Check if the connected user is a member of this project.
   */
  /*def IsMemberOf(project: Long)(f: => String => Request[AnyContent] => Result) = IsAuthenticated { user => request =>
    if(Project.isMember(project, user)) {
      f(user)(request)
    } else {
      Results.Forbidden
    }
  }*/

  /**
   * Check if the connected user is a owner of this task.
   */
  /*def IsOwnerOf(task: Long)(f: => String => Request[AnyContent] => Result) = IsAuthenticated { user => request =>
    if(Task.isOwner(task, user)) {
      f(user)(request)
    } else {
      Results.Forbidden
    }
  }*/

}

object IfAjax extends ActionModule0 {
  override implicit def onFail(req: Request[AnyContent]) = play.api.mvc.Results.BadRequest("This request can only be an Ajax request")
  def condition(req: Request[AnyContent]): Boolean = 
    req.headers.get("x-requested-with").map{_ == "xmlhttprequest"}.getOrElse(false)
}
object IfAuth extends ActionModule1[User] {
  override implicit def onFail(req: Request[AnyContent]) = Results.Redirect(routes.Application.login)
  def map(req: Request[AnyContent]) = List(req.session.get("email").flatMap { email => store.getUserByEmail(email) getOrElse sys.error("was not a Success") })
}
object IfNotAuth extends ActionModule0 {
  def condition(req: Request[AnyContent]) = req.session.get("email") == None
}
object OptionAuth extends ActionModule1[Option[User]] {
  def map(req: Request[AnyContent]): List[Option[Option[User]]] = List(req.session.get("email").map { email => store.getUserByEmail(email) getOrElse sys.error("was not a Success") })
}

// For testing, doesn't really check Job ownership for now
object OwnsJob extends ActionModule2[User, Job] {
  val strategy = EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(), 
      name="demo strategy",
      entrypoint=URL("http://www.w3.org"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
      
  //implicit def onFail(req: Request[AnyContent]) = Results.Redirect(routes.Application.login)
  implicit def jobId: Job#Id = null
  override def map(req: Request[AnyContent]): List[Option[_]] = 
    IfAuth.map(req).head match {
      case Some(user) => List(Some(user), Some(new Job(strategy = strategy)))
      case _ => List(None, None)
    }
}




// Temporary objects to test compilation
object True extends ActionModule0 {
  def condition(req: Request[AnyContent]): Boolean = true
}
object False extends ActionModule0 {
  def condition(req: Request[AnyContent]): Boolean = false
}
object Hello extends ActionModule1[String] {
  def map(req: Request[AnyContent]) = List(Some("hello"))
}
object HiAndHello extends ActionModule2[String, String] {
  def map(req: Request[AnyContent]) = List(Some("hi"), Some("hello"))
}
object GoodByeFolks extends ActionModule3[String, String, String] {
  def map(req: Request[AnyContent]) = List(Some("good"), Some("bye"), Some("folks"))
}
object HiGoodByeFolks extends ActionModule4[String, String, String, String] {
  def map(req: Request[AnyContent]) = List(Some("hi"), Some("good"), Some("bye"), Some("folks"))
}
object Test {
  
  def test() = {
    val a0 = True
    val a1 = Hello
    val a2 = HiAndHello
    val a3 = GoodByeFolks
    
    val b: ActionModule0 = a0 >> a0 >> a0 >> a0 // composing ActionModule0s produce an ActionModule0
    val c = a0 >> a1                            // ActionModule0 >> ActionModule1 produce an ActionModule1 
    val d = a1 >> a0                            // Can be composed in whichever order
    val e = a1 >> a1                            // -> ActionModule2
    val f = a1 >> a2                            // -> ActionModule3
    val g = a1 >> a3                            // -> ActionModule4
    val h = e >> a0 >> a1                       // -> ActionModule4
    val result = play.api.mvc.Results.Ok
    
    b {req => result}
    c {req => s: String => result}
    d {req => s: String => result}
    e {req => s: String => ss: String => result}
    f {req => s: String => ss: String => sss: String => result}
    g {req => s: String => ss: String => sss: String => ssss: String => result}
  }
  
}