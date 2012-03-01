package org.w3.vs.assertor

import org.w3.vs.model._
import org.w3.vs.run._
import scala.io.Source

/** An assertor that can read an [[org.w3.vs.validator.Assertion]] from a file
 * it's useful only for testing as nothing is really asserted here
 */
object FromFileAssertor extends FromUnicornFormatAssertor {

  val id = AssertorId("FromFileAssertor")
  
  def observe(file: java.io.File): Events =
    this.assert(Source.fromFile(file))

}