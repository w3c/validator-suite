package org.w3.vs.model

import org.w3.vs._
import org.w3.util._
import akka.dispatch._
import org.w3.banana._
import scalaz._
import scalaz.Scalaz._

case class Strategy (
    entrypoint: URL,
    linkCheck: Boolean,
    maxResources: Int,
    filter: Filter = Filter.includeEverything,
    assertorSelector: AssertorSelector = AssertorSelector.simple) {
  
  def mainAuthority: Authority = entrypoint.authority
  
  def getActionFor(url: URL): HttpAction =
    if (filter.passThrough(url)) {
      if (url.authority === entrypoint.authority)
        GET
      else if (linkCheck)
        HEAD
      else
        IGNORE
    } else {
      IGNORE
    }

}
