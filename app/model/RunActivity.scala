package org.w3.vs.model

import scalaz._

object RunActivity {
  implicit val equalRunActivity: Equal[RunActivity] = Equal.equalA[RunActivity]
  def apply(s: String): RunActivity = s match {
    case "running" => Running
    case "idle" => Idle
  }
}

sealed trait RunActivity {
  override def toString = this match {
    case Running => "running"
    case Idle => "idle"
  }
}

case object Running extends RunActivity

case object Idle extends RunActivity
