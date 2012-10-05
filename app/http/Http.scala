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

  /**
   * a Fetch command message for an Http actor
   *
   * The type for token could be anything (the Http actor just passes it to the
   * response) but this gives little type-safety
   *
   * @param url the URL of the Web resource to fetch
   * @param action the kind of HTTP action
   * @token a token that will be included in the response from the actor
   */
  case class Fetch(url: URL, method: HttpMethod, token: RunId)

  case class SetSleepTime(value: Long)

  case object HowManyPendingRequests

  case object GetOn

  case object GetOff

  case object PutOn

  case object PutOff

}

import Http._

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class Http(httpClient: AsyncHttpClient, scheduler: Scheduler) extends Actor with PathAwareActor {

  val logger = Logger.of(classOf[Http])

  val cache = Cache(new java.io.File("/tmp/http-cache"))

  def getAuthorityManagerRefOrCreate(authority: Authority): ActorRef = {
    val encoded = encode(authority)
    try {
      context.children.find(_.path.name === encoded) getOrElse {
        context.actorOf(Props(new AuthorityManager(authority, httpClient, scheduler, cache)), name = encoded)
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

