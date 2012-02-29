package org.w3.vs.controllers

import org.w3.vs.model.User
import org.w3.vs.prod.configuration.store

import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.mvc.RequestHeader

import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue

trait ActionModule[A] extends Composable[A, ActionReq, ActionRes, Action[AnyContent]]
trait SocketModule[A] extends Composable[A, SocketReq, SocketRes, WebSocket[JsValue]]

object IfAjax extends ActionModule[Boolean] {
  def extract(req: ActionReq) = {
    req.headers.get("x-requested-with") match {
      case Some("xmlhttprequest") => Right(true)
      case _ => Left(play.api.mvc.Results.BadRequest("This request can only be an Ajax request"))
    }
  }
}
object IfNotAjax extends ActionModule[Boolean] {
  def extract(req: ActionReq) = {
    req.headers.get("x-requested-with") match {
      case None => Right(true)
      case _ => Left(play.api.mvc.Results.BadRequest("This request cannot be an Ajax request"))
    }
  }
}
object OptionAjax extends ActionModule[Option[Boolean]] {
  def extract(req: ActionReq) = {
    req.headers.get("x-requested-with") match {
      case Some("xmlhttprequest") => Right(Some(true))
      case _ => Right(None)
    }
  }
}

object IfAuth extends ActionModule[User] { 
  def extract(req: Request[AnyContent]) = {
    req.session.get("email").flatMap{store.getUserByEmail(_).right.get} match {
      case Some(user) => Right(user)
      case _ => Left(Results.Redirect(controllers.routes.Application.login))
    }
  }
}
object IfAuthSocket extends SocketModule[User] {
  def extract(req: RequestHeader) = {
    req.session.get("email").flatMap{store.getUserByEmail(_).right.get} match {
      case Some(user) => Right(user)
      case _ => Left((Iteratee.foreach[JsValue](e => println(e)), Enumerator.eof))
    }
  }
}
object IfNotAuth extends ActionModule[Boolean] {
  def extract(req: ActionReq) = {
    req.session.get("email") match { 
      case None => Right(true)
      case _ => Left(Results.Redirect(controllers.routes.Validator.index)) 
    }
  }
}
object OptionAuth extends ActionModule[Option[User]] {
  def extract(req: ActionReq) = {
    Right(req.session.get("email").flatMap{store.getUserByEmail(_).right.get})
  }
}