package org.w3.vs.observer.message

import org.w3.util.URL
import org.w3.vs.assertor.AssertorId

sealed trait BroadcastMessage

// states
case object ObservationFinished extends BroadcastMessage
case object InitialState extends BroadcastMessage
case object Stopped extends BroadcastMessage
// events
case class URLsToExplore(nbUrls: Int) extends BroadcastMessage
case class URLsToObserve(nbUrls: Int) extends BroadcastMessage
case class FetchedGET(url: URL, httpCode: Int, extractedURLs: Int) extends BroadcastMessage
case class FetchedHEAD(url: URL, httpCode: Int) extends BroadcastMessage
case class FetchedError(url: URL, errorMessage: String) extends BroadcastMessage
case class Asserted(
    url: URL,
    assertorId: AssertorId,
    numberOfErrors: Int,
    numberOfWarnings: Int) extends BroadcastMessage
case class AssertedError(url: URL, assertorId: AssertorId, t: Throwable) extends BroadcastMessage
case class NothingToObserve(url: URL) extends BroadcastMessage
