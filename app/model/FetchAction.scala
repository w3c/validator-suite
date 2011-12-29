package org.w3.vs.model

sealed trait FetchAction

case object FetchGET extends FetchAction
case object FetchHEAD extends FetchAction
case object FetchNothing extends FetchAction
