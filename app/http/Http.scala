package org.w3.vs.http

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import org.w3.util._
import akka.util.Duration
import akka.util.duration._
import play.Logger
import org.w3.vs.observer.Observer
import org.w3.vs.model.{FetchAction, FetchHEAD, FetchGET}
import org.w3.vs.ValidatorSuiteConf

trait Http {
  def fetch(url: URL, action: FetchAction, observer: Observer): Unit
  def authorityManagerFor(url: URL): AuthorityManager
  def authorityManagerFor(authority: Authority): AuthorityManager
}

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class HttpImpl()(implicit configuration: ValidatorSuiteConf) extends Http with TypedActor.PostStop {

  import configuration.httpClient
  // TODO really???
  import TypedActor.dispatcher
  
  val logger = Logger.of(classOf[Http])
  
  var registry = Map[Authority, AuthorityManager]()
  
  def authorityManagerFor(url: URL): AuthorityManager =
    authorityManagerFor(url.authority)
  
  def authorityManagerFor(authority: Authority): AuthorityManager = {
    registry.get(authority).getOrElse {
      val authorityManager = TypedActor(TypedActor.context).typedActorOf(
        classOf[AuthorityManager],
        new AuthorityManagerImpl(authority),
        Props(),
        authority)
      registry += (authority -> authorityManager)
      authorityManager
    }
  }
  
  def fetch(url: URL, action: FetchAction, observer: Observer): Unit =
    authorityManagerFor(url).fetch(url, action, observer)
  
  override def postStop = {
    logger.debug("closing asyncHttpClient")
    httpClient.close()
  }
  
}

