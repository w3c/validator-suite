package org.w3.vs.model

import scalaz.Equal

sealed trait HttpAction
case object IGNORE extends HttpAction
case object GET extends HttpAction
case object HEAD extends HttpAction

object HttpAction {

  implicit val equalHttpAction: Equal[HttpAction] = Equal.equalA[HttpAction]

  def apply(s: String): HttpAction = s match {
    case "IGNORE" => IGNORE
    case "GET" => GET
    case "HEAD" => HEAD
  }

}
