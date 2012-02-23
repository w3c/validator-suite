package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.model._
import org.w3.vs.observer._

/**
 * An update that happened on an Observation.
 * 
 * Listening to updates lets you know everything about an Observation.
 */
sealed trait ObservationUpdate

/**
 * The Observation has finished, there should be no more updates after that.
 * 
 * Note: this may go away in next versions.
 */
case object Done extends ObservationUpdate

/**
 * The user has stopped the observation
 */
case object Stopped extends ObservationUpdate

/**
 * A coherent state for an Observation.
 */
case class ObservationSnapshot(updates: Iterable[ObservationUpdate]) extends ObservationUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResourceInfo(resourceInfo: ResourceInfo) extends ObservationUpdate

/**
 * A new Assertion was received
 */
case class NewAssertion(assertion: Assertion) extends ObservationUpdate
