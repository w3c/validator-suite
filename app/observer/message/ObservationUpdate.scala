package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId
import org.w3.vs.model.Response
import org.w3.vs.observer.ObserverState

sealed trait ObservationUpdate

// states
case object ObservationFinished extends ObservationUpdate
case class ObservationSnapshot(
    numberOfResponses: Int,
    numberOfUrlsToBeExplored: Int,
    numberOfAssertions: Int,
    updates: Iterable[ObservationUpdate]) extends ObservationUpdate
case object Stopped extends ObservationUpdate
// events
case class NewURLsToExplore(urls: Iterable[URL]) extends ObservationUpdate
case class NewURLsToObserve(nbUrls: Int) extends ObservationUpdate
case class NewResponse(response: Response) extends ObservationUpdate
case class NewAssertion(assertion: ObserverState#Assertion) extends ObservationUpdate
