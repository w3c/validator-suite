package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

/**
 * A AssertorPicker that knows when to pick the HTMLValidator
 */
object SimpleAssertorPicker extends AssertorPicker {
  
  def pick(optContentType: Option[ContentType]): Iterable[FromURLAssertor] = {
    optContentType match {
      case Some("text/html") | Some("application/xhtml+xml") =>
        Seq(HTMLValidator)
      case _ => Seq()
    }
  }
  
}