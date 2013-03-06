package org.w3.vs.model

import scalaz.Equal

object DoneReason {
  implicit val equal = Equal.equalA[DoneReason]
}

sealed trait DoneReason

case object Cancelled extends DoneReason

case object Completed extends DoneReason
