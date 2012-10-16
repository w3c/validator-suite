package org.w3.vs.util

object Util {

  implicit class DurationW(n: Int) {
    import scala.concurrent.util.Duration
    def second: Duration = seconds
    def seconds: Duration = Duration(n, java.util.concurrent.TimeUnit.SECONDS)
  }

}
