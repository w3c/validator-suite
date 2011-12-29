package org.w3.vs.observer

/**
 * The state of an observer. Can be one of: 
 * <ul>
 * <li>ExplorationState</li>
 * <li>ObservationState</li>
 * <li>FinishedState</li>
 * <li>ErrorState</li>
 * </ul>
 */
sealed trait ObserverState

case object ExplorationState extends ObserverState
case object ObservationState extends ObserverState
case object FinishedState extends ObserverState
case object ErrorState extends ObserverState