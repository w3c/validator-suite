package org.w3.vs.run

/**
 * The phase of an run.
 */
sealed trait RunState

case object Starting extends RunState
case object Running extends RunState
case object Idle extends RunState
case object Stopped extends RunState