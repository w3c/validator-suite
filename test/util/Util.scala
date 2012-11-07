package org.w3.vs.util

object Util {

  implicit class DurationW(n: Int) {
    import scala.concurrent.duration.{ FiniteDuration, Duration }
    def second: FiniteDuration = seconds
    def seconds: FiniteDuration = Duration(n, java.util.concurrent.TimeUnit.SECONDS)
  }

}
