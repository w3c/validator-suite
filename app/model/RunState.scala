package org.w3.vs.model

//case class RunState(activity: RunActivity, explorationMode: ExplorationMode)

sealed trait RunActivity
case object Busy extends RunActivity
case object Idle extends RunActivity

sealed trait ExplorationMode
case object ProActive extends ExplorationMode
case object Lazy extends ExplorationMode
