package org.w3.vs.model

import scalaz._

object RunActivity {

  implicit val equalOrganizationId: Equal[RunActivity] = new Equal[RunActivity] {
    def equal(left: RunActivity, right: RunActivity): Boolean = left == right
  }

}

sealed trait RunActivity
case object Busy extends RunActivity
case object Idle extends RunActivity

object ExplorationMode {

  implicit val equalOrganizationId: Equal[ExplorationMode] = new Equal[ExplorationMode] {
    def equal(left: ExplorationMode, right: ExplorationMode): Boolean = left == right
  }

}

sealed trait ExplorationMode
case object ProActive extends ExplorationMode
case object Lazy extends ExplorationMode
