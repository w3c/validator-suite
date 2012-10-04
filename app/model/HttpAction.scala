package org.w3.vs.model

import scalaz.Equal

sealed trait HttpMethod
sealed trait HttpAction
case object IGNORE extends HttpAction
case object GET extends HttpAction with HttpMethod
case object HEAD extends HttpAction with HttpMethod

object HttpMethod {

  implicit val equalHttpMethod: Equal[HttpMethod] = Equal.equalA[HttpMethod]

  def apply(s: String): HttpMethod = s match {
    case "GET" => GET
    case "HEAD" => HEAD
  }

  def fromString(s: String): Option[HttpMethod] = s match {
    case "GET" => Some(GET)
    case "HEAD" => Some(HEAD)
    case _ => None
  }

}

object HttpAction {
 
  implicit val equalHttpAction: Equal[HttpAction] = Equal.equalA[HttpAction]
  
  def apply(s: String): HttpAction = s match {
    case "IGNORE" => IGNORE
    case "GET" => GET
    case "HEAD" => HEAD
  }
  
}
