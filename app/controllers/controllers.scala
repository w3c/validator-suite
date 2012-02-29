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

package object controllers {
  type ActionReq = Request[AnyContent]
  type SocketReq = RequestHeader
  type ActionRes = Result
  type SocketRes = (Iteratee[JsValue,_], Enumerator[JsValue])
  
  implicit def action: ((ActionReq => ActionRes) => Action[AnyContent]) = Action.apply _
  implicit def socket: ((SocketReq => SocketRes) => WebSocket[JsValue]) = WebSocket.using[JsValue] _
  
  def CloseWebsocket: SocketRes = (Iteratee.ignore[JsValue], Enumerator.eof)
}