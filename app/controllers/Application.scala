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
import org.w3.vs.run._
import play.api.libs._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.json.JsValue
import play.api.mvc.WebSocket.FrameFormatter
import scala.PartialFunction
import org.w3.vs.prod.configuration.store
import org.w3.vs.controllers._

object Application extends Controller {
  
  val logger = play.Logger.of("Controller.Application")

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
    implicit request => t => Ok(views.html.login(loginForm))
  }
  
  /**
   * Logout and clean the session.
   */
  def logout = Action {
    Redirect(routes.Application.login).withNewSession.flashing(
      "success" -> "You've been logged out"
    )
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

}