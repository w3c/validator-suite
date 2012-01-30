package org.w3.vs.assertor

import org.w3.util._
import org.w3.vs.model._
import scala.io.Source

/** An assertor that returns assertions about a document pointed by a URL
 */
trait FromURLAssertor extends Assertor with FromUnicornFormatAssertor {

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
  def assert(url: URL): Asserted = {
    val source = Source.fromURL(validatorURLForMachine(url))
    this.assert(source)
  }
  
}