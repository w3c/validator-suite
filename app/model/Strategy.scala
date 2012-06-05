package org.w3.vs.model

import org.w3.vs._
import org.w3.vs.assertor._
import org.w3.util._
import org.w3.util.Headers._
import scalaz.Scalaz._
import akka.dispatch._
import org.w3.banana._

object Strategy {
  
  def getStrategyVO(id: StrategyId)(implicit conf: VSConfiguration): FutureVal[Exception, StrategyVO] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val uri = StrategyUri(id)
    FutureVal.applyTo(conf.store.getNamedGraph(uri)) flatMapValidation { graph => 
      val pointed = PointedGraph(uri, graph)
      StrategyVOBinder.fromPointedGraph(pointed)
    }
  }

  def get(id: StrategyId)(implicit conf: VSConfiguration): FutureVal[Exception, Strategy] =
    getStrategyVO(id) map { Strategy(_) }

  def saveStrategyVO(vo: StrategyVO)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] = {
    import conf.binders._
    implicit val context = conf.webExecutionContext
    val graph = StrategyVOBinder.toPointedGraph(vo).graph
    val result = conf.store.addNamedGraph(StrategyUri(vo.id), graph)
    FutureVal.toFutureValException(FutureVal.applyTo(result))
  }

  def save(strategy: Strategy)(implicit conf: VSConfiguration): FutureVal[Exception, Unit] =
    saveStrategyVO(strategy.toValueObject)
  
  def apply(vo: StrategyVO)(implicit conf: VSConfiguration): Strategy = {
    import vo._
    Strategy(id, entrypoint, linkCheck, maxResources, filter)
  }

}

case class Strategy (
    id: StrategyId = StrategyId(),
    entrypoint: URL,
    linkCheck: Boolean,
    maxResources: Int,
    filter: Filter = Filter.includeEverything,
    assertorsFor: AssertorSelector = AssertorSelector.simple)(implicit conf: VSConfiguration) {
  
  val distance: Int = 50
  
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
  
  def toValueObject: StrategyVO = StrategyVO(id, entrypoint, distance, linkCheck, maxResources, filter)
  
}
