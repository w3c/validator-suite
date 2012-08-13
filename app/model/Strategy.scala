package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import akka.dispatch._
import org.w3.banana._

case class Strategy (
    entrypoint: URL,
    linkCheck: Boolean,
    maxResources: Int,
    filter: Filter = Filter.includeEverything,
    assertorSelector: AssertorSelector = AssertorSelector.simple) {
  
  val mainAuthority: Authority = entrypoint.authority
  
  val authorityToObserve: Authority = mainAuthority
  
  def getActionFor(url: URL): HttpAction =
    if (filter.passThrough(url)) {
      if (url.authority == entrypoint.authority)
        GET
      else if (linkCheck)
        HEAD
      else
        IGNORE
    } else {
      IGNORE
    }

  def noAssertor(): Strategy = this.copy(assertorSelector = AssertorSelector.noAssertor)
  
}
