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

  def testError400(): ActionA = DevAction { implicit req =>
    BadRequest(views.html.error._400(List(("debug.unexpected", "blah blah blah"))))
  }

  def testError404(): ActionA = DevAction { implicit req =>
    NotFound(views.html.error._404())
  }
  
  def testError50x(): ActionA = DevAction { implicit req =>
    InternalServerError(views.html.error._50x())
  }

  def testError500(): ActionA = DevAction { implicit req =>
    InternalServerError(views.html.error._500(List(("error", "blah blah blah"))))
  }

  def testError503(): ActionA = DevAction { implicit req =>
    ServiceUnavailable(views.html.error._503())
  }

}
