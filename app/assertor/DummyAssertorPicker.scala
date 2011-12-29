package org.w3.vs.assertor

import org.w3.util._

/**
 * An AssertorPicker that always returns the DummyAssertor
 * When Content-Type == text/html
 */
object DummyAssertorPicker extends AssertorPicker {
  
  def pick(optContentType: Option[ContentType]): Iterable[FromURLAssertor] = {
    optContentType match {
      case Some("text/html") => Seq(DummyAssertor)
      case _ => Seq()
    }
  }
  
}
