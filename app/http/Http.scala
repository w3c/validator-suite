package org.w3.vs.http

import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import org.w3.util._
import akka.util.Duration
import akka.util.duration._
import play.Logger
import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import scalaz.Scalaz._
import org.w3.util.akkaext._
import AuthorityManager.encode

case class Fetch(url: URL, action: HttpAction, runId: RunId, jobId: JobId)
case class SetSleepTime(value: Long)
case object HowManyPendingRequests

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class Http()(implicit configuration: VSConfiguration) extends Actor with PathAwareActor {

  val httpClient = configuration.httpClient

  // TODO really???
  import TypedActor.dispatcher
  
  val logger = Logger.of(classOf[Http])

  def getAuthorityManagerRefOrCreate(authority: Authority): ActorRef = {
    val encoded = encode(authority)
    try {
      context.children.find(_.path.name === encoded) getOrElse {
        context.actorOf(Props(new AuthorityManager(authority)), name = encoded)
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
    case fetch @ Fetch(url, _, _, _) => {
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

