package org.w3.vs.assertor

import org.w3.vs.model.AssertorId

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  val id: AssertorId = AssertorId() 
  val name: String
}
