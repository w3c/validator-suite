package org.w3.vs.util

import java.io._
import scala.concurrent.{ Future, ExecutionContext }
import com.codahale.metrics._
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.util.{ Success, Failure, Try }
import org.w3.vs.web._

/** utility functions to build timers */
object timer {

  val logger = play.Logger.of("org.w3.vs.util.timer")

  /* use the following timer for synchronous tasks */

  def timer[T](name: String)(body: => T): T = {
    val start = System.currentTimeMillis
    val result = body
    val end = System.currentTimeMillis
    logger.debug(name + ": " + (end - start))
    result
  }

  def timer[T](t: Timer)(body: => T): T = {
    val context = t.time()
    val result = body
    context.stop()
    result
  }

  def timer[T](name: String, t: Timer)(body: => T): T = {
    timer(t) { timer(name)(body) }
  }

  implicit class FutureF[+T](val future: Future[T]) extends AnyVal {

    def getOrFail(duration: Duration = Duration("60s")): T = {
      Await.result(future, duration)
    }

    /**
     * logs (DEBUG) how long the Future took to be completed
     */
    def timer(name: String)(implicit ec: ExecutionContext): Future[T] = {
      val start = System.currentTimeMillis
      future onComplete { case _ =>
        val end = System.currentTimeMillis
        logger.debug(name + ": " + (end - start) + "ms")
      }
      future
    }

    /**
     * configures the timer metrics for the given Future
     */
    def timer(timer: Timer)(implicit ec: ExecutionContext): Future[T] = {
      val context = timer.time()
      future onComplete { case _ => context.stop() }
      future
    }

  }

}
