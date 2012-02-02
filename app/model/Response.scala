package org.w3.vs.model

import org.w3.util._

sealed trait Response {
  val url: URL
  def tinyString = toString 
}

case class HttpResponse(
    url: URL,
    httpVerb: HttpVerb,
    status: Int,
    headers: Headers,
    links: List[URL]) extends Response {
  
  override def tinyString =
    "[%s %d headers %d links]" format (url.toString, headers.size, links.size)

}

case class ErrorResponse(url: URL, typ: String) extends Response

object Response {
  
  def averageDelay(timestamps: Iterable[Long]): Long = {
    val sorted = timestamps.toSeq.sorted
    sorted.size match {
      case 0 | 1 => sys.error("You can't call this function with 0 or 1 timestamps")
      case _ => {
        val delays = sorted zip sorted.tail map { case (t1, t2) => t2 - t1 }
        (delays.foldLeft(0L)(_ + _) / delays.size.toFloat).toLong
      }
    }
  }
  
}
