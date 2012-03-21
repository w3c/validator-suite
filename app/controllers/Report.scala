//package controllers
//
//import org.w3.vs.model.Job
//import org.w3.vs.actor.Run
//import org.w3.vs.actor.message._
//import org.w3.vs.controllers._
//import play.api.mvc.Controller
//import play.api.mvc.WebSocket
//import play.api.libs.json.JsValue
//import play.api.libs.iteratee.Iteratee
//import play.api.libs.iteratee.Enumeratee
//
//object Report extends Controller {
//  
//  def store = org.w3.vs.Prod.configuration.store
//  
//  val logger = play.Logger.of("Controller.Validator")
//  
//  // TODO: make the implicit explicit!!!
//  import org.w3.vs.Prod.configuration
//  
//  implicit def ec = configuration.webExecutionContext
//  
//  def subscribe(id: Job#Id): WebSocket[JsValue] = IfAuthSocket { request => user =>
//    configuration.runCreator.byJobId(id).map { run: Run =>
//      val in = Iteratee.foreach[JsValue](e => println(e))
//      val enumerator = run.subscribeToUpdates()
//      (in, enumerator &> Enumeratee.map[RunUpdate]{ e => e.toJS })
//    }.getOrElse(CloseWebsocket)
//  }
//  
//}
