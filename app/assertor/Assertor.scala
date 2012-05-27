package org.w3.vs.assertor

import org.w3.vs._
import org.w3.vs.model.AssertorId

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  // TODO Fix this.
  implicit val conf = org.w3.vs.Prod.configuration
  implicit val executionContext = conf.assertorExecutionContext  
  
  val id: AssertorId = AssertorId() 
  val name: String
}
