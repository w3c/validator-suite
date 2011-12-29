package org.w3.vs.assertor

import org.w3.vs.model._
import scala.io.Source
import akka.dispatch._

/** An assertor that can assert something from a Source
 */
trait FromSourceAssertor extends Assertor {

  /** returns an Assertion from the given source
   *
   *  @param source where to read 
   *  @return the assertion
   */
  def assert(source: Source): Future[Assertion]

}