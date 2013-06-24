package org.w3.vs.model

import org.w3.vs.util._
import org.w3.vs.web._
import scalaz.Equal

object ResourceInfo {

  def apply(response: ResourceResponse): ResourceInfo = response match {
    case ErrorResponse(_, _, why) => InfoError(why)
    case HttpResponse(url, _, status@(301|302|303|307), headers, _, _) => {
      headers.location match {
        case Some(location) => try Redirect(status, (url / location).get) catch { case e: Exception => InfoError(location + " is not a valid URL") }
        case None => InfoError(url.toString + ": couldn't find a Location header")
      }
    }
    case HttpResponse(_, _, status, _, _, _) => Fetched(status)
  }

}

sealed trait ResourceInfo {
  implicit val equal = Equal.equalA[ResourceInfo]
}

case class Fetched(status: Int) extends ResourceInfo

case class Redirect(status: Int, url: URL) extends ResourceInfo

case class InfoError(why: String) extends ResourceInfo
