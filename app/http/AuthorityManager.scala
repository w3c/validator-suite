package org.w3.vs.http

import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import org.w3.util._
import org.w3.vs.observer._
import akka.util.Duration
import akka.util.duration._
import java.lang.System.currentTimeMillis
import play.Logger
import org.w3.vs.model.ObserverId
import org.w3.vs.GlobalSystem
import org.w3.vs.GlobalSystem

trait AuthorityManager {
  def GET(url: URL, distance: Int, actionManagerId: String): Unit
  def HEAD(url: URL, actionManagerId: String): Unit
  def sleepTime: Long
  def sleepTime_= (value: Long): Unit
}

class AuthorityManagerImpl private[http] (
  authority: Authority,
  client: AsyncHttpClient)
extends AuthorityManager with TypedActor.PostStop {
  
  val logger = Logger.of(classOf[AuthorityManager])
  
  var _sleepTime: Long = 1000L
  
  var lastFetchTimestamp = 0L
  
  def sleepTime = _sleepTime
  
  def sleepTime_= (value: Long): Unit = {
    _sleepTime = value
  }
  
  def sleepIfNeeded(body: => Unit): Unit = {
    val current = currentTimeMillis()
    val needToSleep = _sleepTime - (current - lastFetchTimestamp)
    if (needToSleep > 0)
      Thread.sleep(needToSleep)
    body
    lastFetchTimestamp = current
  }
  
  def observer(observerId: String) =
    GlobalSystem.observerCreator.byObserverId(ObserverId(observerId)).get
  
  def GET(url: URL, distance: Int, observerId: String): Unit = sleepIfNeeded {
    val f = Http.GET(client, url) onSuccess {
      case r: GETResponse =>
        observer(observerId).sendGETResponse(url, r)
    } onFailure {
      case t: Throwable =>
        observer(observerId).sendException(url, t)
    }
    try {
      Await.result(f, 10 seconds)
    } catch {
      case te: java.util.concurrent.TimeoutException =>
        observer(observerId).sendException(url, te)
    }
  }
  
  def HEAD(url: URL, observerId: String): Unit = sleepIfNeeded {
    val f = Http.HEAD(client, url) onSuccess {
      case r: HEADResponse =>
        observer(observerId).sendHEADResponse(url, r)
    } onFailure {
      case t: Throwable =>
        observer(observerId).sendException(url, t)
    }
    try {
      Await.result(f, 10 seconds)
    } catch {
      case te: java.util.concurrent.TimeoutException =>
        observer(observerId).sendException(url, te)
    }
  }
  
  override def postStop = {
    logger.debug("%s: stopping manager" format authority)
  }
  
}

