package org.w3.vs.observer

/**
 * The phase of an observer.
 */
sealed trait ObserverState

case object Running extends ObserverState
case object Idle extends ObserverState
case object Stopped extends ObserverState