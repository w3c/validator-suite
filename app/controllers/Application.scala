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

object Application extends Controller {
  
  val logger = play.Logger.of("Controller.Application")
  
  // -- Authentication

  val loginForm = Form(
    tuple(
      "email" -> text,
      "password" -> text
    ) verifying ("Invalid email or password", result => result match {
      case (email, password) => User.authenticate(email, password).isDefined
    })
  )

  /**
   * Login page.
   */
  def login = Action { implicit request =>
    Ok(views.html.login(loginForm))
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

  /**
   * Redirect to login if the user in not authorized.
   */
  private def onUnauthorized(request: RequestHeader) = Results.Redirect(routes.Application.login)
  
  /**
   * Simply close the websocket if the user is not authorized.
   */
  private def onUnauthorizedWebSocket[A](request: RequestHeader) = CloseWebsocket[A]
  protected def CloseWebsocket[A]: (Iteratee[A, _], Enumerator[A]) = (Iteratee.foreach[A](e => println(e)), Enumerator.eof)
  
  /** 
   * Action for authenticated users.
   */
  def IsAuthenticated(f: => String => Request[AnyContent] => Result): Action[(Action[AnyContent], AnyContent)] = Security.Authenticated(username, onUnauthorized) { user =>
    Action(request => f(user)(request))
  }
  
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
