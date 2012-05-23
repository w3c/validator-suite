package org.w3.vs.model

import org.w3.util._
import org.w3.vs.assertor._
import org.w3.util._
import org.w3.util.Headers._
import scalaz.Scalaz._
import akka.dispatch._

object Strategy {
  
  def apply(
      id: StrategyId = StrategyId(),
      entrypoint: URL,
      distance: Int,
      linkCheck: Boolean,
      maxNumberOfResources: Int,
      filter: Filter,
      assertorsFor: AssertorSelector = AssertorSelector.simple): Strategy = 
    Strategy(StrategyVO(id, entrypoint, distance, linkCheck, maxNumberOfResources, filter), assertorsFor)
  
  def get(id: StrategyId): FutureVal[Exception, Strategy] = sys.error("")
  def save(strategy: Strategy)(implicit context: ExecutionContext): FutureVal[Exception, Strategy] = sys.error("")
  
}

/** A [[org.w3.vs.model.ActionStrategy]] made of an entry point URL and a distance from it.
  * 
  * @param uuid
  * @param name
  * @param delay
  * @param entrypoint the entry point defining this [[org.w3.vs.model.ExplorationStrategy]]
  * @param distance the maximum distance the crawler should move away from `entrypoint`
  * @param filter a filter to be applied to the URLs being discovered
  */
case class StrategyVO(
    id: StrategyId = StrategyId(),
    entrypoint: URL,
    distance: Int,
    linkCheck: Boolean,
    maxNumberOfResources: Int,
    filter: Filter)

case class Strategy(
    valueObject: StrategyVO,
    assertorsFor: AssertorSelector) {
  
  def id: StrategyId = valueObject.id
  def entrypoint: URL = valueObject.entrypoint
  def distance: Int = valueObject.distance
  def linkCheck: Boolean = valueObject.linkCheck
  def maxNumberOfResources: Int = valueObject.maxNumberOfResources
  def filter: Filter = valueObject.filter
  
  val mainAuthority: Authority = entrypoint.authority
  
  val authorityToObserve: Authority = mainAuthority
  
  def fetch(url: URL, distance: Int): HttpAction =
    if (filter.passThrough(url)) {
      if ((url.authority == entrypoint.authority) &&
          (distance <= this.distance))
        GET
      else if (linkCheck)
        HEAD
      else
        IGNORE
    } else {
      IGNORE
    }

  def noAssertor(): Strategy = this.copy(assertorsFor = AssertorSelector.noAssertor)

}
