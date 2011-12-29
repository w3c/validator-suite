package org.w3.vs.observer

import scala.collection.mutable.Set
import akka.actor.TypedActor

/**
 * A Subscriber that can subscribe to an Observer
 * Then the Observer can broadcast message to the Subcriber
 */
trait ObserverSubscriber {
  def subscribe(): Unit
  def unsubscribe(): Unit
  def broadcast(msg: String): Unit
}

trait ObserverSubscribers extends ObserverImpl {
  
  var subscribers = Set[ObserverSubscriber]()
  
  def subscribe(subscriber: ObserverSubscriber): Unit = {
    subscribers += subscriber
    logger.debug("%s: (subscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
    subscriber.broadcast(toBroadcast(InitialState))
  }
  
  def unsubscribe(subscriber: ObserverSubscriber): Unit = {
    subscribers -= subscriber
    logger.debug("%s: (unsubscribe) known broadcasters %s" format (shortId, subscribers.mkString("{", ",", "}")))
  }

  def toBroadcast(msg: BroadcastMessage): String = msg match {
    case URLsToExplore(nb) => """["NB_EXP", %d]""" format (nb)
    case URLsToObserve(nb) => """["NB_OBS", %d]""" format (nb)
    case FetchedGET(url, httpCode, extractedURLs) => """["GET", %d, "%s", %d]""" format (httpCode, url, extractedURLs)
    case FetchedHEAD(url, httpCode) => """["HEAD", %d, "%s"]""" format (httpCode, url)
    case FetchedError(url, errorMessage) => """["ERR", "%s", "%s"]""" format (errorMessage, url)
    case Asserted(url, assertorId, errors, warnings/*, validatorURL*/) => """["OBS", "%s", "%s", %d, %d]""" format (url, assertorId, errors, warnings)
    case AssertedError(url, assertorId, t) => """["OBS_ERR", "%s"]""" format url
    case NothingToObserve(url) => """["OBS_NO", "%s"]""" format url
    case ObservationFinished => """["OBS_FINISHED"]"""
    case InitialState => {
      val initial = """["OBS_INITIAL", %d, %d, %d, %d]""" format (responses.size, pendingFetches.size + urlsToBeExplored.size, _assertions.size, pendingAssertions.size)
      import org.w3.vs.model._
      val responsesToBroadcast = responses map {
        // disctinction btw GET and HEAD, links.size??
        case (url, HttpResponse(_, status, _, _)) =>
          toBroadcast(FetchedGET(url, status, 0))
        case (url, ErrorResponse(_, typ)) =>
          toBroadcast(FetchedError(url, typ))
      }
      val assertionsToBroadcast = _assertions map {
        case (url, assertorId, Left(t)) =>
          toBroadcast(AssertedError(url, assertorId, t))
        case (url, assertorId, Right(assertion)) =>
          toBroadcast(Asserted(url, assertorId, assertion.errorsNumber, assertion.warningsNumber))
      }
      (List(initial) ++ responsesToBroadcast ++ assertionsToBroadcast) mkString ""
    }
    
  }
  
  def broadcast(msg: BroadcastMessage): Unit = {
    val tb = toBroadcast(msg)
    if (subscribers != null)
      subscribers foreach (_.broadcast(tb))
    else
      logger.debug("%s: no more broadcaster for %s" format (shortId, tb))
  }
  
  override def conditionalEndOfAssertionPhase(): Boolean = {
    val b = super.conditionalEndOfAssertionPhase()
    if (b) {
      subscribers foreach (TypedActor.stop(_))
      subscribers = null
    }
    b
  }
}