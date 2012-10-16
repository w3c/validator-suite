package org.w3.vs.model

import scalaz.Equal

object ExplorationMode {
  implicit val equalExplorationMode: Equal[ExplorationMode] = Equal.equalA[ExplorationMode]
  def apply(s: String): ExplorationMode = s match {
    case "proactive" => ProActive
    case "lazy" => Lazy
  }
}

sealed trait ExplorationMode {
  override def toString = this match {
    case ProActive => "proactive"
    case Lazy => "lazy"
  }
}

case object ProActive extends ExplorationMode

case object Lazy extends ExplorationMode
