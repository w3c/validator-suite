package org.w3.vs.model

import scalaz.{Ordering => OrderingZ, _}
import scalaz.Scalaz._

sealed trait AssertionSeverity {
  override def toString: String = this match {
    case Error => "error"
    case Warning => "warning"
    case Info => "info"
  }
}
case object Error extends AssertionSeverity
case object Warning extends AssertionSeverity
case object Info extends AssertionSeverity

object AssertionSeverity {

  implicit val equal = Equal.equalA[AssertionSeverity]

  def apply(severity: String): AssertionSeverity = {
    severity.toLowerCase.trim match {
      case "error" => Error
      case "warning" => Warning
      case "info" => Info
      case _ => Info // TODO log
    }
  }

  implicit val ordering: Ordering[AssertionSeverity] = new Ordering[AssertionSeverity] {
    def compare(x: AssertionSeverity, y: AssertionSeverity): Int = {
      (x, y) match {
        case (a, b) if (a === b) => 0
        case _@(Error, _) => +1
        case _@(Info, _) => -1
        case _@(Warning, Info) => +1
        case _@(Warning, Error) => -1
        case _@(Warning, Warning) => 0 // compilation complains without this
      }
    }
  }
}

