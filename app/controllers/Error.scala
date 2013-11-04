package controllers

import play.api.mvc.Action

object Error extends VSController {

  val logger = play.Logger.of("controllers.Error")

  def error400 = Action { implicit req => Ok(views.html.error._400()) }
  def error403 = Action { implicit req => Ok(views.html.error._403()) }
  def error404 = Action { implicit req => Ok(views.html.error._404()) }
  def error50x = Action { implicit req => Ok(views.html.error._50x()) }
  def error500 = Action { implicit req => Ok(views.html.error._500()) }
  def error503 = Action { implicit req => Ok(views.html.error._503()) }

}
