package controllers

import play.api.mvc.Controller

object Report extends Controller {
  
  def store = org.w3.vs.Prod.configuration.store
  
  val logger = play.Logger.of("Controller.Validator")
  
  // TODO: make the implicit explicit!!!
  import org.w3.vs.Prod.configuration
  
  implicit def ec = configuration.webExecutionContext
  
}