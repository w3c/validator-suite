package org.w3.vs.http

import com.ning.http.client._
import akka.actor._
import akka.dispatch._
import org.w3.util._
import org.w3.vs.actor._
import akka.util.Duration
import akka.util.duration._
import java.lang.System.currentTimeMillis
import play.Logger
import org.w3.vs.model.{Response => _, _}
import org.w3.vs.VSConfiguration
import scala.collection.mutable.Queue
import org.w3.util.akkaext._

object AuthorityManager {

  def encode(authority: Authority): String =
    authority.replaceAll(":", "_")

}

final class AuthorityManager(authority: Authority)(implicit configuration: VSConfiguration) extends Actor {
  
  val httpClient = configuration.httpClient

  val logger = Logger.of(classOf[AuthorityManager])
  
  var sleepTime: Long = 1000L
  
  val queue = Queue[(ActorRef, Fetch)]()

  var lastFetchTimestamp: Long = 0L

  def current(): Long = currentTimeMillis()

  def needsToSleep(): Long = {
    sleepTime - (current() - lastFetchTimestamp)
  }

  def needToSleep() = needsToSleep > 0

  var pendingTick = false

  def scheduleTick(): Unit =
    if (sleepTime > 0 && needsToSleep() > 0) {
      configuration.system.scheduler.scheduleOnce(needsToSleep().millis, self, 'Tick)
      pendingTick = true
    }

  def receive: Actor.Receive = {

    case SetSleepTime(value) => {
      // logger.debug(authority + " setting sleep time to " + value + " milliseconds")
      sleepTime = value
      lastFetchTimestamp = 0L
    }

    case fetch: Fetch if queue.nonEmpty => {
      queue.enqueue((sender, fetch))
      if (! pendingTick) logger.error("the queue is not empty but there is no pending Tick")
    }

    case fetch: Fetch if needToSleep() => {
      // logger.debug(authority+" need to sleep")
      queue.enqueue((sender, fetch))
      scheduleTick()
    }

    case fetch @ Fetch(url, action, runId) => {
      doFetch(sender, url, action, runId)
    }

    case 'Tick if queue.isEmpty => {
      logger.error("received a Tick but there was nothing in the queue")
    }

    case 'Tick => {
      val (thesender, Fetch(url, action, runId)) = queue.dequeue()
      doFetch(thesender, url, action, runId)
      if (queue.nonEmpty)
        scheduleTick()
      else
        pendingTick = false
    }

    case HowManyPendingRequests => {
      sender ! queue.size
    }

  }


  final def doFetch(to: ActorRef, url: URL, action: HttpVerb, runId: RunId): Unit = {

    lastFetchTimestamp = current()
    
    val httpHandler: AsyncHandler[Unit] = new AsyncHandler[Unit]() {
      
      val builder = new Response.ResponseBuilder()
      
      var finished = false
        
      // We can have onThrowable called because onCompleted
      // throws, and other complex situations, so to handle everything
      // we use this
      private def finish(body: => Unit): Unit = {
        if (!finished) {
          try {
            body
          } catch {
            case t: Throwable => {
              to ! KoResponse(url, action, t, runId)
              throw t // rethrow for benefit of AsyncHttpClient
            }
          } finally {
            finished = true
          }
        }
      }

      // this can be called if any of our other methods throws,
      // including onCompleted.
      def onThrowable(t: Throwable): Unit = {
        finish { throw t }
      }

      def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.STATE = {
        builder.accumulate(bodyPart)
        AsyncHandler.STATE.CONTINUE
      }
      
      def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.STATE = {
        builder.accumulate(responseStatus)
        AsyncHandler.STATE.CONTINUE
      }
      
      def onHeadersReceived(responseHeaders: HttpResponseHeaders): AsyncHandler.STATE = {
        builder.accumulate(responseHeaders)
        AsyncHandler.STATE.CONTINUE
      }
        
      def onCompleted(): Unit = {
        import scala.collection.JavaConverters._
        
        finish {
          val response = builder.build()
          import java.util.{Map => jMap, List => jList}
          import scala.collection.JavaConverters._
          val status = response.getStatusCode()
          val headers: Headers =
            (response.getHeaders().asInstanceOf[jMap[String, jList[String]]].asScala mapValues { _.asScala.toList }).toMap
          val body = response.getResponseBody()
          val fetchResponse = OkResponse(url, action, status, headers, body, runId)
          to ! fetchResponse
        }
      }
    }
      
    action match {
      case GET => httpClient.prepareGet(url.externalForm).execute(httpHandler)
      case HEAD => httpClient.prepareHead(url.externalForm).execute(httpHandler)
    }

  }

  


  override def postStop() = {
    logger.debug("%s: stopping manager" format authority)
  }
  
}

