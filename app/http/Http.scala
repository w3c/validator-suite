package org.w3.vs.http

import akka.actor._
import org.w3.util._
import play.Logger
import org.w3.vs.model._
import com.ning.http.client._
import scalaz.Scalaz._
import org.w3.util.akkaext._
import AuthorityManager.encode

object Http {

  val logger = Logger.of(classOf[Http])

  case class SetSleepTime(value: Long)

  case object HowManyPendingRequests

}

import Http._

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class Http(httpClient: AsyncHttpClient, scheduler: Scheduler, cacheOpt: Option[Cache]) extends Actor with PathAwareActor {

  import Http.logger

  def getAuthorityManagerRefOrCreate(authority: Authority): ActorRef = {
    val encoded = encode(authority)
    try {
      context.children.find(_.path.name === encoded) getOrElse {
        context.actorOf(Props(new AuthorityManager(authority, httpClient, scheduler, cacheOpt)).withDispatcher("http-dispatcher"), name = encoded)
      }
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / encoded)
    }
  }

  def receive = {
    case Tell(Child(name), msg) => {
      val authorityManagerRef = getAuthorityManagerRefOrCreate(name)
      authorityManagerRef forward msg
    }
    case fetch @ Fetch(url, _, _) => {
      val authority = url.authority
      val authorityManagerRef = getAuthorityManagerRefOrCreate(authority)
      authorityManagerRef forward fetch
    }
  }
  
  override def postStop() = {
    logger.debug("closing asyncHttpClient")
    httpClient.close()
  }
  
}

