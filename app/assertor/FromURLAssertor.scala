package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source
import scalaz.Validation

/**
 * An assertor that returns assertions about a document pointed by a URL
 */
trait FromURLAssertor extends FromSourceAssertor {
  
  /**
   * returns the URL to be used by a machine to validate
   * the given URL against this assertor
   */
  def validatorURLForMachine(url: URL, configuration: AssertorConfiguration): URL
  
  /**
   * returns the URL to be used by a human to validate
   * the given URL against this assertor
   * The default is validatorURLForMachine and you can override it
   */
  def validatorURLForHuman(url: URL, configuration: AssertorConfiguration): URL = validatorURLForMachine(url, configuration)
  
  /** 
   *  @param url a pointer to the document
   *  @return the assertion
   */
  def assert(url: URL, configuration: AssertorConfiguration): Iterable[Assertion] = {
    val source = Source.fromURL(validatorURLForMachine(url, configuration))
    assert(source)
  }
  
}
