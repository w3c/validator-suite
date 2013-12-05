package org.w3.vs.view

import org.joda.time._
import org.joda.time.format._
import play.api.Play._
import play.api.i18n.Messages
import org.w3.vs.web.URL
import java.util.ResourceBundle
import scala.util.Random
import scala.collection.immutable.Iterable

object Helper {

  def config = current.configuration

  def notWord(boolean: Boolean): String = {
    if (boolean) "" else "not "
  }

  def shorten(string: String, limit: Int): String = {
    if (string.size > limit) {
      val dif = string.size - limit
      string.substring(0, limit/2) +
        """…""" +
        string.substring(string.size - limit/2)
    } else string
  }

  def shorten(url: URL, limit: Int): String = {
     shorten(url.toString.replaceFirst("http://", ""), limit)
  }

  val TimeFormatter: DateTimeFormatter = try {
    DateTimeFormat.forPattern(Messages("time.pattern"))
  } catch { case _: Exception =>
    DateTimeFormat.forPattern("dd MMM yy' at 'H:mm' UTC'")
  }

  def formatTime(time: DateTime): String = TimeFormatter.print(time)

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
    }.flatten.mkString("&")
  }

  def parseQueryString(queryString: String): Map[String, Seq[String]] = {
    val a: Seq[(String, String)] = queryString.replaceFirst("^\\?", "").split("&").toSeq.map{a =>
      val Array(k, v) = a.split("=")
      (k, v)
    }
    a.groupBy(_._1).map{case (k, v) => (k, v.map(_._2))}.toMap
  }

  case class Testimonial(author: String, message: String)

  def testimonials: Iterable[Testimonial] = {
    import scala.collection.JavaConversions._
    val messages = ResourceBundle.getBundle("testimonials")
    val keys: Iterator[String] = messages.getKeys
    val testimonials: Iterable[Testimonial] = keys.toSeq.groupBy(key => key.split("""\.""")(0)).map{ case (prefix, keys) =>
      Testimonial(
        author = messages.getString(keys(keys.indexOf(s"${prefix}.cite"))),
        message = messages.getString(keys(keys.indexOf(s"${prefix}.message")))
      )
    }
    Random.shuffle(testimonials).take(3)
  }

  /*def clearParam(param: String)(implicit req: Request[_]): String = {
    queryString(req.queryString - param)
  }*/

}
