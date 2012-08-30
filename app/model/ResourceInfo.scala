package org.w3.vs.model

import org.w3.util._
import scalaz._
import scalaz.Scalaz._

object ResourceInfo {

  def apply(response: ResourceResponse): ResourceInfo = response match {
    case ErrorResponse(_, _, why) => InfoError(why)
    case HttpResponse(url, _, status, headers, _) if status == 301 || status == 303 => {
      headers get "Location" flatMap { _.headOption } match {
        case Some(location) => try Redirect(status, URL(location)) catch { case _ => InfoError(location + " is not a valid URL") }
        case None => InfoError(url.toString + ": couldn't find a Location header")
      }
    }
    case HttpResponse(_, _, status, _, _) => Fetched(status)
  }

}

sealed trait ResourceInfo {
  implicit val equal = Equal.equalA[ResourceInfo]
}

case class Fetched(status: Int) extends ResourceInfo

case class Redirect(status: Int, url: URL) extends ResourceInfo

case class InfoError(why: String) extends ResourceInfo
