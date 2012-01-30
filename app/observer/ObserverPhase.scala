package org.w3.vs.observer

/**
 * The phase of an observer.
 */
sealed trait ObserverPhase

case object NotYetStarted extends ObserverPhase
case object ExplorationPhase extends ObserverPhase
case object AssertionPhase extends ObserverPhase
case object Finished extends ObserverPhase
case object Error extends ObserverPhase
case object Interrupted extends ObserverPhase