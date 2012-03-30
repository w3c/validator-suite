package org.w3.vs.view

// import org.w3.vs.model.Job
// import akka.dispatch.Await
// import akka.util.duration._
// import org.w3.vs.actor.Stopped
// import akka.pattern.AskTimeoutException
// import org.w3.vs.actor._

case class ReportSection(header: ReportHeader, list: Either[List[ReportValue], List[ReportSection]])

sealed trait ReportHeader {
	def aside: ReportAside
}
case class UrlHeader(title: String, aside: ReportAside) extends ReportHeader
case class ContextHeader(code: String, aside: ReportAside) extends ReportHeader
case class AssertorHeader(assertor: String, aside: ReportAside) extends ReportHeader
case class MessageHeader(title: String, aside: ReportAside, severity: String, assertor: String) extends ReportHeader

trait ReportAside
case object EmptyAside extends ReportAside
case class OccurrenceAside(occurences: Int) extends ReportAside
case class ErrorWarningAside(errors: Int, warnings: Int) extends ReportAside
case class OccurrenceResourceAside(occurences: Int, resources: Int) extends ReportAside
case class FirstLineColAside(line: Option[Int], column: Option[Int]) extends ReportAside

sealed trait ReportValue
case class PositionValue(line: Option[Int], column: Option[Int]) extends ReportValue
case class ContextValue(code: String, line: Option[Int], column: Option[Int]) extends ReportValue

object Helper {
  
  def generateSections(sections: List[ReportSection]): String = {
    sections.map {section =>
      val id = java.util.UUID.randomUUID.toString.substring(0,6)
"""		<article id="%s" tabindex="1">""".format(id) + """ 
		<div>
""" + generateHeader(section.header, id) + generateAside(section.header.aside) + """
		</div>
		<div>
""" + {section.list match {
        case Left(list) => generateValues(list)
        case Right(list) => generateSections(list)
      }} + """
		</div>
		</article>
"""}.mkString("")
  }
  
  def generateHeader(header: ReportHeader, id: String): String = {
    header match {
      case MessageHeader(title, aside, severity, assertor) => """<a href="#%s" class="group">%s</a>""".format(id, title)
      case UrlHeader(title, aside) => """<a href="#%s" class="group url">%s</a>""".format(id, title)
      case ContextHeader(code, aside) => """<a href="#%s" class="group context">%s</a>""".format(id, code)
      case AssertorHeader(assertor, aside) => """<a href="#%s" class="group assertor">%s</a>""".format(id, assertor)
    }
  }
  
  def generateValues(values: List[ReportValue]): String = {
"""	<ul class="messages">
""" + values.map {
      case ContextValue(code, line, col) => {
        val context = 
          line.map(l => """<span class="line">Line: %s</span>""" format (l)).getOrElse("") +
          col.map(c => """<span class="column">Column: %s</span>""" format (c)).getOrElse("") +
	      (if(code != "") """<code class="context">%s</code>""" format (code) else "")
	    if (context != "") "<li>" + context + "</li>"
      }
      case PositionValue(line, col) => {
        val position = 
          line.map(l => """<span class="line">Line: %s</span>""" format (l)).getOrElse("") +
          col.map(c => """<span class="column">Column: %s</span>""" format (c)).getOrElse("")
	    if (position != "") "<li>" + position + "</li>"
      }
    }.mkString("\n") + """
	</ul>"""
  }
  
  def generateAside(aside: ReportAside): String = {
    aside match {
      case ErrorWarningAside(errors, warnings) => """
			<aside><span class="errors">%s</span><span class="warning">%s</span></aside>""" format (errors, warnings)
      case OccurrenceAside(occurrences) => """
			<aside><span class="occurrences">%s</span></aside>""" format (occurrences)
      case OccurrenceResourceAside(occurrences, resources) => """
			<aside><span class="occurrences">%s</span><span class="resources">%s</span></aside>""" format (occurrences, resources)
      case FirstLineColAside(line, col) => """
			<aside><span class="line">%s</span><span class="col">%s</span></aside>""" format (line, col)
      case EmptyAside => ""
    }
  }
  
}
