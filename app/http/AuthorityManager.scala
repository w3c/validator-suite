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

case class SleepTime(value: Long)

class AuthorityManager(authority: Authority)(implicit configuration: VSConfiguration) extends Actor {
  
  val httpClient = configuration.httpClient

  val logger = Logger.of(classOf[AuthorityManager])
  
  var sleepTime: Long = 1000L
  
  var lastFetchTimestamp = 0L

  val queue = Queue[Fetch]()
  
  // def sleepIfNeeded(body: => Unit): Unit = {
  //   val current = currentTimeMillis()
  //   val needToSleep = _sleepTime - (current - lastFetchTimestamp)
  //   if (needToSleep > 0)
  //     Thread.sleep(needToSleep)
  //   body
  //   lastFetchTimestamp = current
  // }
  
  def receive = {

    case SleepTime(value) => {
      this.sleepTime = value
    }

    case fetch @ Fetch(url: URL, action: HttpVerb, runId: RunId) => {

      val to = sender

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
        case GET => httpClient.prepareGet(url.toExternalForm()).execute(httpHandler)
        case HEAD => httpClient.prepareHead(url.toExternalForm()).execute(httpHandler)
      }

    }
      
  }
  


  override def postStop() = {
    logger.debug("%s: stopping manager" format authority)
  }
  
}

