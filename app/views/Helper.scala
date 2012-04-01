package org.w3.vs.view

// import org.w3.vs.model.Job
// import akka.dispatch.Await
// import akka.util.duration._
// import org.w3.vs.actor.Stopped
// import akka.pattern.AskTimeoutException
// import org.w3.vs.actor._

case class ReportSection(header: ReportHeader, list: Either[List[ReportValue], List[ReportSection]])

sealed trait ReportHeader
case class UrlHeader(title: String) extends ReportHeader
case class ContextHeader(code: String) extends ReportHeader
case class AssertorHeader(assertor: String) extends ReportHeader
case class MessageHeader(title: String, severity: String, assertor: String) extends ReportHeader

//trait ReportAside
//case object EmptyAside extends ReportAside
//case class OccurrenceAside(occurences: Int) extends ReportAside
//case class ErrorWarningAside(errors: Int, warnings: Int) extends ReportAside
//case class OccurrenceResourceAside(occurences: Int, resources: Int) extends ReportAside
//case class FirstLineColAside(line: Option[Int], column: Option[Int]) extends ReportAside

sealed trait ReportValue
case class PositionValue(line: Option[Int], column: Option[Int]) extends ReportValue
case class ContextValue(code: String, line: Option[Int], column: Option[Int]) extends ReportValue

object Helper {
  
  def generateSections(sections: List[ReportSection]): String = {
    sections.map {section =>
      val id = java.util.UUID.randomUUID.toString.substring(0,6)
"""		<article id="%s" tabindex="1">""".format(id) + """ 
		<div>
""" + generateHeader(section.header, Some(id)) + generateAsideFor(section) + """
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
""" + generateHeader(section.header) + generateAsideFor(section) + """
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
        case MessageHeader(title, severity, assertor) => """<a href="#%s" class="group message %s">%s</a>""".format(id.get, severity, title)
        case UrlHeader(title) => """<a href="#%s" class="group url">%s</a>""".format(id.get, title)
        case ContextHeader(code) => """<a href="#%s" class="group context">%s</a>""".format(id.get, if (code != "") code else "Empty context")
        case AssertorHeader(assertor) => """<a href="#%s" class="group assertor">%s</a>""".format(id.get, assertor)
      }
    } else {
      header match {
        case MessageHeader(title, severity, assertor) => """<span class="group message %s">%s</span>""".format(severity, title)
        case UrlHeader(title) => """<span class="group url">%s</span>""".format(title)
        case ContextHeader(code) => """<span class="group context">%s</span>""".format( if (code != "") code else "Empty context")
        case AssertorHeader(assertor) => """<span class="group assertor">%s</span>""".format(assertor)
      }
    }
  }
  
  def generateValues(values: List[ReportValue]): String = {
"""	<ul class="messages">
""" + values.map {
      case ContextValue(code, line, col) => {
        val context = 
          """<span class="pos">Position: """ +
          """<span class="line" title="Line %s">%s</span>""".format(line.getOrElse("not specified"), line.getOrElse("-")) +
          """<span class="col" title="Column %s">%s</span>""".format(col.getOrElse("not specified"), col.getOrElse("-")) +
          """</span>""" +
	      (if(code != "") """<code class="context">%s</code>""" format (code) else "")
	    "<li>" + context + "</li>"
      }
      case PositionValue(line, col) => {
        val position =
          """<span class="pos">Position: """ +
          """<span class="line" title="Line %s">%s</span>""".format(line.getOrElse("not specified"), line.getOrElse("-")) +
          """<span class="col" title="Column %s">%s</span>""".format(col.getOrElse("not specified"), col.getOrElse("-")) +
          """</span>"""
	    "<li>" + position + "</li>"
      }
    }.mkString("\n") + """
	</ul>"""
  }
  
  def generateAsideFor(section: ReportSection): String = {
    section.list match {
      case Left(list) => {
        // 10 occurrences
        if (list.size > 1) """<aside><span class="occurrences">%s</span> times</aside>""" format list.size else ""
      }
      case Right(list) => {
        section.header match {
          case MessageHeader(title, severity, assertor) => {
            // HtmlValisator | 10 occurrences in 3 resources
            val values = countValues(list)
"""			<aside>
"""         + (if (values > 1) {
"""        		<span class="occurrences">%s</span> times
"""         } format (values) else {""}) + countSubs(list) +
"""        		<span class="assertor">%s</span>
        	</aside>
"""         format (assertor)
          }
          case UrlHeader(url) => {
            // 25 | 50 (3 assertors)
            val errors = countType("error", list)
            val warnings = countType("warning", list)
            val info = countType("info", list)
"""			<aside>""" + 
            (if (errors > 0) {
"""				<span class="errors count" title="%s errors">%s</span>
"""         format (errors, errors) } else "") + (if (warnings > 0) {
"""				<span class="warnings count" title="%s warnings">%s</span>
"""         format (warnings, warnings) } else "") + (if (info > 0) {
"""				<span class="info count" title="%s info">%s</span>
"""         format (info, info) } else "") +
            {if (info == 0 && errors == 0 && warnings == 0) {
              val values = countValues(list)
              if (values > 1) {
"""				<span class="occurrences">%s</span> times 
"""           format (values)} else ""} else ""} + countSubs(list) +
"""			</aside>"""
          }
          case ContextHeader(code) => {
            val values = countValues(list)
"""			<aside>
"""         + (if (values > 1) {
"""        		<span class="occurrences">%s</span> times
"""         } else {""}) + countSubs(list) +
"""        	</aside>
"""         format (values)
          }
          case AssertorHeader(assertor) => {
            // 25 | 50 (3 assertors)
            val errors = countType("error", list)
            val warnings = countType("warning", list)
            val info = countType("info", list)
"""			<aside>
"""         + countSubs(list) +
            (if (errors > 0) {
"""				<span class="errors count" title="%s errors">%s</span>
"""         format (errors, errors) } else "") + (if (warnings > 0) {
"""				<span class="warnings count" title="%s warnings">%s</span>
"""         format (warnings, warnings) } else "") + (if (info > 0) {
"""				<span class="info count" title="%s info">%s</span>
"""         format (info, info) } else "") + 
"""			</aside>"""
          }
        }
      }
    }
  }
  
  def countValues(sections: List[ReportSection]): Int = sections.map(sect => countValues(sect)).reduce(_+_)
  def countValues(section: ReportSection): Int = {
    section.list.fold(
      values => values.size,
      sections => sections.map(sect => countValues(sect)).reduce(_+_)
    )
  }
  def countResources(sections: List[ReportSection]): Int = sections.map(sect => countResources(sect)).reduce(_+_)
  def countResources(section: ReportSection): Int = {
    section.header match {
      case UrlHeader(_) => 1
      case _ => section.list.fold(values => 0, sections => countResources(sections)) 
    }
  }
  def countType(typ: String, sections: List[ReportSection]): Int = sections.map(sect => countType(typ, sect)).reduce(_+_)
  def countType(typ: String, section: ReportSection): Int = {
    section.header match {
      case MessageHeader(title, severity, assertor) => {
         if (severity == typ) countValues(section)
         else 0
      } 
      case _ => section.list.fold(values => 0, sections => countType(typ, sections)) 
    }
  }
  def countSubs(sections: List[ReportSection]): String = {
    if (sections.size > 1) {
      sections(0).header match {
        case UrlHeader(_) => """ in <span class="resourceCount">""" + sections.size + "</span> resources"
        case ContextHeader(_) => """ in <span class="contextCount">""" + sections.size + "</span> contexts"
        case AssertorHeader(_) => """ <span class="assertorCount" title="Results for """+sections.size+""" assertors">(""" + sections.size + ")</span>"
        case _ => ""
      }
    } else ""
  }
  
}
