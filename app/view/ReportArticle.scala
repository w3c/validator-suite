package org.w3.vs.view

import org.w3.vs._
import org.w3.util._
import org.w3.vs.model._
import org.joda.time._

sealed trait ReportArticle
case class URLReportArticle(
    url: URL,
    lastValidated: DateTime,
    warnings: Int,
    errors: Int) extends ReportArticle
    
object URLReportArticle {
  
  def apply(t: (URL, DateTime, Int, Int)): URLReportArticle =
      URLReportArticle(t._1, t._2, t._3, t._4)
  
}