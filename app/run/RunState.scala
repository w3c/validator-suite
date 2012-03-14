package org.w3.vs.run

sealed trait FSMState
case object On extends FSMState
case object Off extends FSMState

sealed trait RunState
case object Busy extends RunState
case object Waiting extends RunState
case object Stopped extends RunState
