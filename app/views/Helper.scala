package org.w3.vs.view

// import org.w3.vs.model.Job
// import akka.dispatch.Await
// import akka.util.duration._
// import org.w3.vs.actor.Stopped
// import akka.pattern.AskTimeoutException
// import org.w3.vs.actor._

case class ReportSection(header: ReportHeader, list: Either[List[ReportValue], List[ReportSection]])

sealed trait ReportHeader {
	var aside: ReportAside
	def withAside(aside: ReportAside) = {
	  this.aside = aside
	  this
	}
}
case class UrlHeader(title: String, var aside: ReportAside) extends ReportHeader
case class ContextHeader(code: String, var aside: ReportAside) extends ReportHeader
case class AssertorHeader(assertor: String, var aside: ReportAside) extends ReportHeader
case class MessageHeader(title: String, var aside: ReportAside, severity: String, assertor: String) extends ReportHeader

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
""" + generateHeader(section.header, Some(id)) + generateAside(section.header.aside) + """
		</div>
		<div>
""" + {section.list match {
        case Left(list) => generateValues(list)
        case Right(list) => generateInnerSections(list)
      }} + """
		</div>
		</article>
"""}.mkString("")
  }
  
  // The only difference is that inner sections don't have an id, an anchor, and a tabindex.
  def generateInnerSections(sections: List[ReportSection]): String = {
    sections.map {section =>
"""		<article>
		<div>
""" + generateHeader(section.header) + generateAside(section.header.aside) + """
		</div>
		<div>
""" + {section.list match {
        case Left(list) => generateValues(list)
        case Right(list) => generateInnerSections(list)
      }} + """
		</div>
		</article>
"""}.mkString("")
  }
  
  def generateHeader(header: ReportHeader, id: Option[String] = None): String = {
    if (id.isDefined) {
      header match {
        case MessageHeader(title, aside, severity, assertor) => """<a href="#%s" class="group message %s">%s</a>""".format(id.get, severity, title)
        case UrlHeader(title, aside) => """<a href="#%s" class="group url">%s</a>""".format(id.get, title)
        case ContextHeader(code, aside) => """<a href="#%s" class="group context">%s</a>""".format(id.get, if (code != "") code else "Empty context")
        case AssertorHeader(assertor, aside) => """<a href="#%s" class="group assertor">%s</a>""".format(id.get, assertor)
      }
    } else {
      header match {
        case MessageHeader(title, aside, severity, assertor) => """<span class="group message %s">%s</span>""".format(severity, title)
        case UrlHeader(title, aside) => """<span class="group url">%s</span>""".format(title)
        case ContextHeader(code, aside) => """<span class="group context">%s</span>""".format( if (code != "") code else "Empty context")
        case AssertorHeader(assertor, aside) => """<span class="group assertor">%s</span>""".format(assertor)
      }
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
			<aside><span class="occurrences">%s</span> occurrences</aside>""" format (occurrences)
      case OccurrenceResourceAside(occurrences, resources) => """
			<aside><span class="occurrences">%s</span><span class="resources">%s</span></aside>""" format (occurrences, resources)
      case FirstLineColAside(line, col) => """
			<aside>First at Line: <span class="line">%s</span> Column: <span class="col">%s</span></aside>""" format (line, col)
      case EmptyAside => ""
    }
  }
  
}
