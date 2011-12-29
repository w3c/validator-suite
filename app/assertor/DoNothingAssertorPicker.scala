package org.w3.vs.assertor

import org.w3.util._

/**
 * An AssertorPick that always return no Assertor
 * Useful when you want to shutdown the Assertion phase
 */
object DoNothingAssertorPicker extends AssertorPicker {
  
  def pick(optContentType: Option[ContentType]): Iterable[FromURLAssertor] = 
    Seq()
  
}