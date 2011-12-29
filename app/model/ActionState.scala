package org.w3.vs.model

sealed trait ActionState
case object Crawling extends ActionState
case object Observing extends ActionState
case object Finished extends ActionState
