package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.util._
import scala.io.Source

/** An assertor that can assert something from a Source
 */
trait FromSourceAssertor extends Assertor {

  /** returns an Assertion from the given source
   *
   *  @param source where to read 
   *  @return the assertion
   */
    // TODO
  import org.w3.vs.Prod.configuration
  implicit def ec = configuration.webExecutionContext
  
  def assert(source: Source): FutureVal[Throwable, Iterable[AssertionClosed]] // TODO type exceptions

}