package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId
import org.w3.vs.model.Response

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
case class URLsToExplore(nbUrls: Int) extends ObservationUpdate
case class URLsToObserve(nbUrls: Int) extends ObservationUpdate
case class NewResponse(response: Response) extends ObservationUpdate
case class Asserted(
    url: URL,
    assertorId: AssertorId,
    numberOfErrors: Int,
    numberOfWarnings: Int) extends ObservationUpdate
case class AssertedError(url: URL, assertorId: AssertorId, t: Throwable) extends ObservationUpdate
case class NothingToObserve(url: URL) extends ObservationUpdate
