package org.w3.util

import java.io._
import scala.concurrent.{ Future, ExecutionContext }
import com.yammer.metrics.core.Timer

object Util {

  val logger = play.Logger.of("org.w3.vs.util.Util")

  /**
   * deletes all the files in a directory (only first level, not recursive)
   */
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }

  def time[T](name: String)(body: => T): T = {
    val start = System.currentTimeMillis
    val result = body
    val end = System.currentTimeMillis
    logger.debug(name + ": " + (end - start))
    result
  }

  implicit class FutureF[+T](val future: Future[T]) extends AnyVal {

    /**
     * logs (DEBUG) how much the Future took to be completed
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
