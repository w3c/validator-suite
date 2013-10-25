package controllers

import play.api.mvc.{AnyContent, Result, Request, Action}
import org.w3.vs.Global
import play.api.Mode
import org.w3.vs.controllers._

object Dev extends VSController {

  val logger = play.Logger.of("controllers.Dev")

  def DevAction(f: Request[AnyContent] => Result) = Action { req =>
    if (Global.vs.mode != Mode.Dev) {
      Global.onHandlerNotFound(req)
    } else {
      f(req)
    }
  }

  def test = DevAction { implicit req =>
    Ok(views.html.test())
  }

  def e503(): ActionA = DevAction { implicit req =>
    ServiceUnavailable(views.html.error._503())
  }

}
