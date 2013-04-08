package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.vs.util._
import scala.io.Source

/** An assertor that can assert something from a Source
 */
trait FromSourceAssertor extends Assertor {

  def assert(source: Source): Iterable[Assertion]

}
