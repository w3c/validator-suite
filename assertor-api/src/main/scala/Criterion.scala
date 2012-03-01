package org.w3.assertor

trait Criterion {

  /**
   * a unique identifier in the scope of the Assertor
   */
  def id: String

  /**
   * intented to be one of error, warning, info
   */
  def severity: String

  /**
   * used to build a java.text.MessageFormat, that will be formatted
   * based on a Locale and the arguments from an Assertion
   */
  def message: String

}
