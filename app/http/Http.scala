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
import org.w3.vs.actor._
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import scalaz.Scalaz._

case class Fetch(url: URL, action: HttpVerb, runId: RunId)

/**
 * This is an actor which encapsulates the AsyncHttpClient library.
 */
class Http()(implicit configuration: VSConfiguration) extends Actor {

  val httpClient = configuration.httpClient

  // TODO really???
  import TypedActor.dispatcher
  
  val logger = Logger.of(classOf[Http])

  def getAuthorityManagerRefOrCreate(authority: Authority): ActorRef = {
    try {
      context.actorOf(Props(new AuthorityManager(authority)), name = authority)
    } catch {
      case iane: InvalidActorNameException => context.actorFor(self.path / authority)
    }
  }

  def receive = {
    case fetch @ Fetch(url, _, _) => {
      val authority = url.authority
      val authorityManagerRef =
        context.children.find(_.path.name === authority) getOrElse getAuthorityManagerRefOrCreate(authority)
      authorityManagerRef forward fetch
    }
  }
  
  override def postStop() = {
    logger.debug("closing asyncHttpClient")
    httpClient.close()
  }
  
}

