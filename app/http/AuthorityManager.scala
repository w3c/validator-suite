package org.w3.vs.http

import com.ning.http.client._
import akka.actor._
import org.w3.util._
import akka.util.duration._
import java.lang.System.currentTimeMillis
import play.Logger
import org.w3.vs.model._
import org.w3.vs.VSConfiguration
import scala.collection.mutable.Queue

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

    case fetch @ Fetch(url, action, context) => {
      doFetch(sender, url, action, context)
    }

    case 'Tick if queue.isEmpty => {
      logger.error("received a Tick but there was nothing in the queue")
    }

    case 'Tick => {
      val (thesender, Fetch(url, action, context)) = queue.dequeue()
      doFetch(thesender, url, action, context)
      if (queue.nonEmpty)
        scheduleTick()
      else
        pendingTick = false
    }

    case HowManyPendingRequests => {
      sender ! queue.size
    }

  }


  final def doFetch(to: ActorRef, url: URL, action: HttpAction, context: (OrganizationId, JobId, RunId)): Unit = {

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
              to ! ErrorResponse(context = context, url = url, action = action, why = t.getMessage)
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

        finish {
          val response = builder.build()
          import java.util.{Map => jMap, List => jList}
          import scala.collection.JavaConverters._
          val status = response.getStatusCode()
          val headers: Headers =
            (response.getHeaders().asInstanceOf[jMap[String, jList[String]]].asScala mapValues { _.asScala.toList }).toMap
          val body = response.getResponseBody()
          val fetchResponse = HttpResponse(context = context, url = url, action = action, status = status, headers = headers, body = body)
          to ! fetchResponse
        }
      }
    }
      
    action match {
      case GET => httpClient.prepareGet(url.externalForm).execute(httpHandler)
      case HEAD => httpClient.prepareHead(url.externalForm).execute(httpHandler)
      case IGNORE => ()
    }

  }

  


  override def postStop() = {
    logger.debug("%s: stopping manager" format authority)
  }
  
}

