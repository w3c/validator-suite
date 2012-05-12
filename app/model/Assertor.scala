package org.w3.vs.model

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  val id: AssertorId
  val name: String
}
