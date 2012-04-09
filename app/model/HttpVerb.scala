package org.w3.vs.model

import scalaz._

object HttpAction {

  implicit val equalHttpAction: Equal[HttpAction] = new Equal[HttpAction] {
    def equal(left: HttpAction, right: HttpAction): Boolean = left == right
  }

}

sealed trait HttpAction

case object FetchNothing extends HttpAction

object HttpVerb {

  implicit val equalHttpVerb: Equal[HttpVerb] = new Equal[HttpVerb] {
    def equal(left: HttpVerb, right: HttpVerb): Boolean = left == right
  }

}

sealed trait HttpVerb

case object GET extends HttpVerb with HttpAction
case object HEAD extends HttpVerb with HttpAction

