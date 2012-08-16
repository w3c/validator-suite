package org.w3.vs.view

import org.w3.vs.model._
import org.w3.util._
import org.joda.time._

sealed trait ReportArticle
case class URLArticle(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends ReportArticle
    
object URLArticle {
  def apply(t: (URL, DateTime, Int, Int)): URLArticle =
    URLArticle(t._1, t._2, t._3, t._4)
}

case class AssertorArticle(
    id: AssertorId,
    name: String,
    warnings: Int,
    errors: Int) extends ReportArticle

object AssertorArticle {
  def apply(t: (AssertorId, String, Int, Int)): AssertorArticle =
    AssertorArticle(t._1, t._2, t._3, t._4)
}





