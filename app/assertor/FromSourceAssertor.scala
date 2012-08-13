package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.util._
import scala.io.Source
import scalaz.Validation

/** An assertor that can assert something from a Source
 */
trait FromSourceAssertor extends Assertor {

  def assert(source: Source): Iterable[Assertion]

}
