package org.w3.vs.view

import org.joda.time._
import org.w3.util.URL

object Helper {
  
  val TimeFormatter = org.joda.time.format.DateTimeFormat.forPattern("MM/dd/yy' at 'K:mma")
  def formatTime(time: DateTime): String = TimeFormatter.print(time).toLowerCase
  def encode(url: URL): String = java.net.URLEncoder.encode(url.toString, "utf-8")

  def shorten(string: String, limit: Int): String = {
    if (string.size > limit) {
      val dif = string.size - limit
      string.substring(0, (string.size - dif)/2) +
        """…""" +
        string.substring((string.size + dif)/2)
    } else string
  }

  def shorten(url: URL, limit: Int): String = {
    shorten(url.toString.replaceFirst("http://", ""), limit)
  }
  
}