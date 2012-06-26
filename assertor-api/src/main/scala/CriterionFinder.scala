package org.w3.assertor


/**
 * a lookup table for Criterions
 * as this is strongly coupled with a particular Assertor,
 * lookups should never fail
 */
trait CriterionFinder {

  def find(criterionId: String): Criterion

  /**
   * all the Criterions known by an Assertor
   */
  def all: java.lang.Iterable[Criterion]

}
