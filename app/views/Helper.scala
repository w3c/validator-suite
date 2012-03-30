package org.w3.vs.view

// import org.w3.vs.model.Job
// import akka.dispatch.Await
// import akka.util.duration._
// import org.w3.vs.actor.Stopped
// import akka.pattern.AskTimeoutException
// import org.w3.vs.actor._

trait ReportListAside
case class ErrorWarningAside(errors: Int, warnings: Int) extends ReportListAside
case class OccurrenceAside(occurences: Int) extends ReportListAside
case class OccurrenceResourceAside(occurences: Int, resources: Int) extends ReportListAside
case object EmptyAside extends ReportListAside
case class FirstLineColAside(line: Option[Int], column: Option[Int]) extends ReportListAside
