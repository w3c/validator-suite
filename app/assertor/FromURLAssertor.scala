package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scalaz._
import Validation._
import scala.io.Source

/** 
 * An assertor that returns assertions about a document pointed by a URL
 */
trait FromURLAssertor extends Assertor {

  /**
   * utility method to encode a URL
   */
  def encodedURL(url: URL): String =
    java.net.URLEncoder.encode(url.toString, "UTF-8")
  
  /**
   * returns the URL to be used by a machine to validate
   * the given URL against this assertor
   */
  def validatorURLForMachine(url: URL): URL
  
  /**
   * returns the URL to be used by a human to validate
   * the given URL against this assertor
   * The default is validatorURLForMachine and you can override it
   */
  def validatorURLForHuman(url: URL): URL = validatorURLForMachine(url)
  
  /** 
   *  @param url a pointer to the document
   *  @return the assertion
   */
  def assert(url: URL): Validation[Throwable, Iterable[RawAssertion]]
  
}

// This is weird. Coupling UnicornFormatAssertor and from FromURLAssertor was not a good idea,
// as it doesn't apply to validator.nu. I think there must be a better way to organize traits here.
trait URLToSourceAssertor extends FromURLAssertor with FromSourceAssertor {
  
  def assert(url: URL) = fromTryCatch {
    val source = Source.fromURL(validatorURLForMachine(url))
    val events = this.assert(source)
    events
  }
  
}