package org.w3.util

import java.io._
import scala.concurrent.{ Future, ExecutionContext }
import com.yammer.metrics.core.Timer

object Util {

  val logger = play.Logger.of("org.w3.vs.util.Util")

  /**
   * deletes all the files in a directory (only first level, not recursive)
   * note: there is still an issue with Windows
   */
  def delete(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles foreach delete
    if (!f.delete())
      throw new FileNotFoundException("Failed to delete file: " + f)
  }

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

  implicit class DurationW(val n: Int) extends AnyVal {
    import scala.concurrent.duration.{ FiniteDuration, Duration }
    def second: FiniteDuration = seconds
    def seconds: FiniteDuration = Duration(n, java.util.concurrent.TimeUnit.SECONDS)
  }

  import java.net.{ URL => jURL }

  implicit class jURLW(val url: jURL) extends AnyVal {
    def withToken(token: String): jURL = new jURL(s"""${url}t0k3n=${token}""")
  }

  implicit class URLW(val url: URL) extends AnyVal {
    def withToken(token: String): URL = URL(s"""${url}t0k3n=${token}""")
  }


}
