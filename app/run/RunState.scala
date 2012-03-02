package org.w3.vs.run

/**
 * The phase of an run.
 */
sealed trait RunState

sealed trait RunStatus

case object NotYetStarted extends RunState with RunStatus
case object Stopped extends RunState with RunStatus
case object Started extends RunState
case object Running extends RunStatus
case object Idle extends RunStatus
