package org.w3.vs.view

import org.joda.time._
import org.joda.time.format._
import org.w3.util.URL
import play.api.i18n.Messages
import play.api.mvc.Request
import java.net.URLEncoder
import collection.immutable.Iterable

object Helper {
  
  def encode(url: URL): String = URLEncoder.encode(url.toString, "utf-8")

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

  val TimeFormatter = try {
    DateTimeFormat.forPattern(Messages("time.pattern"))
  } catch { case _ =>
    DateTimeFormat.forPattern("MM/dd/yy' at 'K:mma")
  }

  def formatTime(time: DateTime): String = TimeFormatter.print(time).toLowerCase

  def formatLegendTime(time: DateTime): String = {
    val diff = DateTime.now(DateTimeZone.UTC).minus(time.getMillis)
    if (diff.getYear > 1970) {
      Messages({if (diff.getYear > 1) "time.legend.year.p" else "time.legend.year"}, diff.getYear)
    } else if (diff.getMonthOfYear > 1) {
      Messages({if (diff.getMonthOfYear > 1) "time.legend.month.p" else "time.legend.month"}, diff.getMonthOfYear)
    } else if (diff.getDayOfMonth > 1) {
      Messages({if (diff.getDayOfMonth > 1) "time.legend.day.p" else "time.legend.day"}, diff.getDayOfMonth)
    } else if (diff.getHourOfDay > 0) {
      Messages({if (diff.getHourOfDay > 1) "time.legend.hour.p" else "time.legend.hour"}, diff.getHourOfDay)
    } else if (diff.getMinuteOfHour > 0) {
      Messages({if (diff.getMinuteOfHour > 1) "time.legend.minute.p" else "time.legend.minute"}, diff.getMinuteOfHour)
    } else /*if (diff.getSecondOfMinute > 0)*/ {
      Messages({if (diff.getSecondOfMinute > 1) "time.legend.second.p" else "time.legend.second"}, diff.getSecondOfMinute)
    }
  }

  def queryString(parameters: Map[String, Seq[String]]): String = {
    parameters.map { case (param, values) =>
        values.map(value => param + "=" + value)
    }.flatten.mkString("?", "&", "")
  }

  def clearParam(param: String)(implicit req: Request[_]): String = {
    queryString(req.queryString - param)
  }

}