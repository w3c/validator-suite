package org.w3.vs

import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.Result
import play.api.mvc.Results
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.mvc.RequestHeader
import play.api.mvc.AsyncResult
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Promise
import play.api.data.format.Formats._
import play.api.data.FormError
import play.api.data.format.Formatter
import org.w3.util._

package object controllers {
  type ActionReq = Request[AnyContent]
  type AsyncActionRes = Promise[Result]
  type SocketRes = (Iteratee[JsValue,_], Enumerator[JsValue])
  
  implicit def action: ((ActionReq => Result) => Action[AnyContent]) = Action.apply _
  implicit def socket: ((RequestHeader => SocketRes) => WebSocket[JsValue]) = WebSocket.using[JsValue] _
  
  def CloseWebsocket: SocketRes = (Iteratee.ignore[JsValue], Enumerator.eof)
    
  implicit val urlFormat = new Formatter[URL] {
    
    override val format = Some("format.url", Nil)
    
    def bind(key: String, data: Map[String, String]) = {
      stringFormat.bind(key, data).right.flatMap { s =>
        scala.util.control.Exception.allCatch[URL]
          .either(URL(s))
          .left.map(e => Seq(FormError(key, "error.url", Nil)))
      }
    }
    
    def unbind(key: String, url: URL) = Map(key -> url.toString)
  }
  
  implicit val booleanFormatter = new Formatter[Boolean] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Boolean] =
      Right(data isDefinedAt key)
    def unbind(key: String, value: Boolean): Map[String, String] =
      if (value) Map(key -> "on") else Map()
  }
  
  import org.w3.vs.Prod.configuration
  implicit def ec = configuration.webExecutionContext
}