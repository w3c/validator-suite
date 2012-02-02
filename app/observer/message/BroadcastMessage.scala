package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId
import org.w3.vs.model.Response

sealed trait BroadcastMessage

// states
case object ObservationFinished extends BroadcastMessage
case class InitialState(
    numberOfResponses: Int,
    numberOfUrlsToBeExplored: Int,
    numberOfAssertions: Int,
    messages: Iterable[BroadcastMessage]) extends BroadcastMessage
case object Stopped extends BroadcastMessage
// events
case class URLsToExplore(nbUrls: Int) extends BroadcastMessage
case class URLsToObserve(nbUrls: Int) extends BroadcastMessage
case class NewResponse(response: Response) extends BroadcastMessage
case class Asserted(
    url: URL,
    assertorId: AssertorId,
    numberOfErrors: Int,
    numberOfWarnings: Int) extends BroadcastMessage
case class AssertedError(url: URL, assertorId: AssertorId, t: Throwable) extends BroadcastMessage
case class NothingToObserve(url: URL) extends BroadcastMessage
