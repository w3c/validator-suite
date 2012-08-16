package org.w3.vs.view

import org.joda.time._
import org.w3.util.URL

object Helper {
  
  val TimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("MM/dd/yy' at 'K:mma")
  def formatTime(time: DateTime): String = TimeFormatter.print(time).toLowerCase
  def encode(url: URL): String = java.net.URLEncoder.encode(url.toString, "utf-8")
  
}