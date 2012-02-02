package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId
import org.w3.vs.model.Response
import org.w3.vs.observer.ObserverState

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
 * 
 * @bertails: this should not be an ObservationUpdate
 * TODO: actually, we should
 * - remove numberOfResponses and numberOfAssertions
 * - send lists of Response and ObserverState#Assertion
 */
case class ObservationSnapshot(
    numberOfResponses: Int,
    numberOfUrlsToBeExplored: Int,
    numberOfAssertions: Int,
    updates: Iterable[ObservationUpdate]) extends ObservationUpdate

/**
 * urls are new URLs to be explored
 */
case class NewURLsToExplore(urls: Iterable[URL]) extends ObservationUpdate

/**
 * New URLs to observe (so coming from the extraction of a link)
 */
case class NewURLsToObserve(nbUrls: Int) extends ObservationUpdate

/**
 * A new Response was received during the exploration
 */
case class NewResponse(response: Response) extends ObservationUpdate

/**
 * A new Assertion was received
 */
case class NewAssertion(assertion: ObserverState#Assertion) extends ObservationUpdate
