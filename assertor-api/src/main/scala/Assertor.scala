package org.w3.assertor

import java.io._


/**
 * A Assertor for a single Web resource seen as a simple "bitstream"
 * it's the generalization of a Validator
 */
trait Assertor {
  
  /**
   * the name of the assertor
   */
  def id: String

  /**
   * the CriterionFinder for this assertor
   */
  def criterionFinder: CriterionFinder

  /**
   * provides Assertions based on the reader
   *
   * it's totally fine to have several assertions sharing the same criterionId
   * 
   * @param reader the document the assertor must read
   * @return a (potentially empty) sequence of Assertions
   */
  def azzert(reader: Reader): java.lang.Iterable[Assertion]

} 


