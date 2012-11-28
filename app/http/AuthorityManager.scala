package org.w3.vs.http

import com.ning.http.client._
import akka.actor._
import org.w3.util._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.lang.System.currentTimeMillis
import org.w3.vs.model._
import scala.collection.mutable.Queue
import scalax.io._
import java.io._
import Http._
import scala.concurrent.ExecutionContext.Implicits.global

object AuthorityManager {

  def encode(authority: Authority): String =
    authority.replaceAll(":", "_")

}

class AuthorityManager(authority: Authority, httpClient: AsyncHttpClient, scheduler: Scheduler, cacheOpt: Option[Cache]) extends Actor {
  
  val logger = play.Logger.of(classOf[AuthorityManager])
  
  var sleepTime: Long = 500L
  
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
      scheduler.scheduleOnce(Duration(needsToSleep(), MILLISECONDS), self, 'Tick)
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

    case fetch @ Fetch(url, action, token) => {
      doFetch(sender, url, action, token)
    }

    case 'Tick if queue.isEmpty => {
      logger.error("received a Tick but there was nothing in the queue")
    }

    case 'Tick => {
      val (thesender, Fetch(url, action, token)) = queue.dequeue()
      doFetch(thesender, url, action, token)
      if (queue.nonEmpty)
        scheduleTick()
      else
        pendingTick = false
    }

    case HowManyPendingRequests => {
      sender ! queue.size
    }

  }


  def doFetch(to: ActorRef, url: URL, method: HttpMethod, token: Any): Unit = {

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
              val errorResponse = ErrorResponse(url = url, method = method, why = t.getMessage)
              to ! (token, errorResponse)
              cacheOpt foreach { _.save(errorResponse) }
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
          val bodyAsBytes = response.getResponseBodyAsBytes()
          def resource = Resource.fromInputStream(new ByteArrayInputStream(bodyAsBytes))
          val httpResponse = HttpResponse(url, method, status, headers, resource)
          to ! (token, httpResponse)
          cacheOpt foreach { _.save(httpResponse, resource) }
        }
      }
    }
      
    method match {
      case GET => httpClient.prepareGet(url.externalForm).execute(httpHandler)
      case HEAD => httpClient.prepareHead(url.externalForm).execute(httpHandler)
    }

  }

  


  override def postStop() = {
    logger.debug("%s: stopping manager" format authority)
  }
  
}

