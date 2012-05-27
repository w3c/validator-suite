package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.assertor._
import org.w3.util._
import org.w3.util.Headers._
import scalaz.Scalaz._
import akka.dispatch._

object Strategy {
  
  def get(id: StrategyId)(implicit conf: VSConfiguration): FutureVal[Exception, Strategy] = sys.error("")
  def save(strategy: Strategy)(implicit conf: VSConfiguration): FutureVal[Exception, Strategy] = sys.error("")
  
}

case class Strategy (
    id: StrategyId = StrategyId(),
    entrypoint: URL,
    distance: Int,
    linkCheck: Boolean,
    maxNumberOfResources: Int,
    filter: Filter,
    assertorsFor: AssertorSelector = AssertorSelector.simple)(implicit conf: VSConfiguration) {
  
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
  
  def toValueObject: StrategyVO = StrategyVO(id, entrypoint, distance, linkCheck, maxNumberOfResources, filter)
  
}
