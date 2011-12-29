package org.w3.vs.observer

import org.w3.vs._
import org.w3.vs.model._
import org.w3.vs.observer._

sealed trait InfoStatus

case class InfoExploration(fetched: Int) extends InfoStatus
case class InfoObservation(partialResults: Assertions) extends InfoStatus
case class InfoFinished(results: Assertions) extends InfoStatus
case object InfoError extends InfoStatus
