package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._

/**
 * Used to find all the Assertors that understand the given Content-Type
 */
trait AssertorPicker {
  
  def pick(optContentType: Option[ContentType]): Iterable[FromURLAssertor]

}
