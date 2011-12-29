package org.w3.vs.assertor

import org.w3.vs._
import org.w3.vs.model._

import com.codecommit.antixml._
import scala.io.Source

/**
 * An assertor as defined in EARL
 * http://www.w3.org/TR/EARL10/#Assertor
 */
trait Assertor {
  
  val id: AssertorId
  
}
