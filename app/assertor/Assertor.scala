package org.w3.vs.assertor

import org.w3.vs.model._

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  
  val id: AssertorId
  
}
