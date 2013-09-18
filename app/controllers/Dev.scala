package controllers

import play.api.mvc.{AnyContent, Result, Request, Action}
import org.w3.vs.Global
import play.api.Mode

object Dev extends VSController {

  val logger = play.Logger.of("org.w3.vs.controllers.Dev")

  def DevAction(f: Request[AnyContent] => Result) = Action { req =>
    if (Global.conf.mode != Mode.Dev) {
      Global.onHandlerNotFound(req)
    } else {
      f(req)
    }
  }

  def test = DevAction { req =>
    Ok(views.html.test())
  }

}
