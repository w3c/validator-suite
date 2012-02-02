package org.w3.vs.model

sealed trait HttpAction

case object FetchNothing extends HttpAction

sealed trait HttpVerb

case object GET extends HttpVerb with HttpAction
case object HEAD extends HttpVerb with HttpAction

