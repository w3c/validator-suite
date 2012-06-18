package org.w3.vs.model

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
  def apply(severity: String): AssertionSeverity = {
    severity.toLowerCase.trim match {
      case "error" => Error
      case "warning" => Warning
      case "info" => Info
      case _ => Info // TODO log
    }
  }
}

