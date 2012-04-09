package org.w3.vs.model

import org.w3.util._

import java.util.UUID

/** A [[org.w3.vs.model.ActionStrategy]] made of an entry point URL and a distance from it.
  * 
  * @param uuid
  * @param name
  * @param delay
  * @param entrypoint the entry point defining this [[org.w3.vs.model.ExplorationStrategy]]
  * @param distance the maximum distance the crawler should move away from `entrypoint`
  * @param filter a filter to be applied to the URLs being discovered
  */
case class EntryPointStrategy(
    uuid: UUID = UUID.randomUUID,
    name: String,
    entrypoint: URL,
    distance: Int,
    linkCheck: Boolean,
    maxNumberOfResources: Int,
    filter: Filter,
    assertorsFor: AssertorSelector = AssertorSelector.simple)
extends Strategy {
  
  def seedURLs: Iterable[URL] = Seq(entrypoint)
  
  def mainAuthority: Authority = entrypoint.getAuthority
  
  val authorityToObserve: Authority = mainAuthority
  
  def fetch(url: URL, distance: Int): HttpAction = {
    if ((url.getAuthority == entrypoint.getAuthority) &&
        (distance <= this.distance))
      GET
    else if (linkCheck)
      HEAD
    else
      FetchNothing
  }

  def noAssertor(): EntryPointStrategy = this.copy(assertorsFor = AssertorSelector.noAssertor)

}
