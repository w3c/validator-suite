package org.w3.vs.http

import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import akka.event.EventHandler
import org.w3.util._
import org.w3.vs.observer._
import akka.util.Duration
import akka.util.duration._
import java.lang.System.currentTimeMillis
import play.Logger

trait AuthorityManager {
  def GET(url: URL, distance: Int, actionManagerId: String): Unit
  def HEAD(url: URL, actionManagerId: String): Unit
  def sleepTime: Long
  def sleepTime_= (value: Long): Unit
}

class AuthorityManagerImpl private[http] (
  authority: Authority,
  client: AsyncHttpClient)
extends TypedActor with AuthorityManager {
  
  val logger = Logger.of(classOf[AuthorityManager])
  
  self.id = authority
  
  var _sleepTime: Long = 1000L
  
  var lastFetchTimestamp = 0L
  
  def actionManager(actionManagerId: String): Observer =
    Actor.registry.typedActorsFor(actionManagerId).head.asInstanceOf[Observer]
  
  def exception(url: URL) =
    new FutureTimeoutException("fetching %s took too long" format url)
  
  def sleepTime = _sleepTime
  
  def sleepTime_= (value: Long): Unit = {
    _sleepTime = value
  }
  
  def GET(url: URL, distance: Int, actionManagerId: String): Unit = {
    val current = currentTimeMillis()
    val needToSleep = _sleepTime - (current - lastFetchTimestamp)
    if (needToSleep > 0)
      Thread.sleep(needToSleep)
    val f = Http.GET(client, url) onResult {
      case r: GETResponse =>
        actionManager(actionManagerId).sendGETResponse(url, r)
    } onException {
      case t: Throwable =>
        actionManager(actionManagerId).sendException(url, t)
    } onTimeout {
      f => actionManager(actionManagerId).sendException(url, exception(url))
    }
    f.await
    lastFetchTimestamp = current
  }
  
  def HEAD(url: URL, actionManagerId: String): Unit = {
    val f = Http.HEAD(client, url) onResult {
      case r: HEADResponse =>
        actionManager(actionManagerId).sendHEADResponse(url, r)
    } onException {
      case t: Throwable =>
        actionManager(actionManagerId).sendException(url, t)
    } onTimeout {
      f => actionManager(actionManagerId).sendException(url, exception(url))
    }
    f.await
    Thread.sleep(sleepTime)
  }
  
  override def postStop = {
    logger.debug("%s: stopping manager" format authority)
  }
  
  override def unhandled (msg: Any): Unit = {
    logger.debug("received " + msg)
  } 
  
}

